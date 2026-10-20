package com.oficinaagua.chatbot_api.llm;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.oficinaagua.chatbot_api.llm.cliente.LlmApiClient;
import com.oficinaagua.chatbot_api.llm.cliente.model.ChatChoice;
import com.oficinaagua.chatbot_api.llm.cliente.model.ChatCompletionRequest;
import com.oficinaagua.chatbot_api.llm.cliente.model.ChatCompletionResponse;
import com.oficinaagua.chatbot_api.llm.cliente.model.ChatMessage;

import feign.FeignException;

/**
 * HU-35: implementa LlmClient con el cliente Feign generado desde llm-chat.yaml.
 * HU-39: reintenta una vez las fallas temporales del proveedor (429, 5xx o sin conexión),
 * detectadas en la prueba de estabilidad. Los timeouts no se reintentan para no duplicar la espera.
 */
@Service
public class ProveedorLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(ProveedorLlmClient.class);

    static final int MAX_INTENTOS = 2;
    private static final long ESPERA_ENTRE_INTENTOS_MS = 1000;

    private final LlmApiClient api;
    private final LlmProperties props;
    private final long esperaMs;

    @Autowired
    public ProveedorLlmClient(LlmApiClient api, LlmProperties props) {
        this(api, props, ESPERA_ENTRE_INTENTOS_MS);
    }

    ProveedorLlmClient(LlmApiClient api, LlmProperties props, long esperaMs) {
        this.api = api;
        this.props = props;
        this.esperaMs = esperaMs;
    }

    @Override
    public String generar(Prompt prompt) {
        ChatCompletionRequest peticion = new ChatCompletionRequest()
                .model(props.modelo())
                .addMessagesItem(new ChatMessage().role("system").content(prompt.sistema()))
                .addMessagesItem(new ChatMessage().role("user").content(prompt.usuario()));

        ResponseEntity<ChatCompletionResponse> respuesta = llamar(peticion);

        String texto = extraerTexto(respuesta.getBody());
        if (texto == null || texto.isBlank()) {
            throw new LlmNoDisponibleException("El LLM devolvió una respuesta vacía");
        }
        return texto.strip();
    }

    private ResponseEntity<ChatCompletionResponse> llamar(ChatCompletionRequest peticion) {
        for (int intento = 1; ; intento++) {
            try {
                return api.crearChatCompletion(peticion);
            } catch (FeignException e) {
                if (intento < MAX_INTENTOS && esReintentable(e)) {
                    log.warn("El LLM falló temporalmente (HTTP {}), reintentando", e.status());
                    esperar();
                    continue;
                }
                throw traducir(e);
            }
        }
    }

    /** Solo fallas temporales: límite de peticiones, errores del servidor o sin conexión. */
    private static boolean esReintentable(FeignException e) {
        if (esTimeout(e)) {
            return false;
        }
        int estado = e.status();
        return estado == 429 || estado >= 500 || estado == -1;
    }

    private void esperar() {
        try {
            Thread.sleep(esperaMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String extraerTexto(ChatCompletionResponse cuerpo) {
        if (cuerpo == null || cuerpo.getChoices() == null || cuerpo.getChoices().isEmpty()) {
            return null;
        }
        ChatChoice primera = cuerpo.getChoices().get(0);
        return primera.getMessage() == null ? null : primera.getMessage().getContent();
    }

    /** Nunca se incluye el cuerpo del error: podría repetir el prompt o datos del proveedor. */
    private static RuntimeException traducir(FeignException e) {
        if (esTimeout(e)) {
            log.warn("El LLM no respondió a tiempo");
            return new LlmTimeoutException("El LLM no respondió a tiempo", e);
        }
        log.warn("El LLM respondió con error HTTP {}", e.status());
        return new LlmNoDisponibleException("El proveedor de LLM falló (HTTP " + e.status() + ")", e);
    }

    private static boolean esTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SocketTimeoutException || t instanceof HttpTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
