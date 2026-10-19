package com.oficinaagua.chatbot_api.llm;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.oficinaagua.chatbot_api.llm.cliente.LlmApiClient;
import com.oficinaagua.chatbot_api.llm.cliente.model.ChatChoice;
import com.oficinaagua.chatbot_api.llm.cliente.model.ChatCompletionRequest;
import com.oficinaagua.chatbot_api.llm.cliente.model.ChatCompletionResponse;
import com.oficinaagua.chatbot_api.llm.cliente.model.ChatMessage;

import feign.FeignException;

/** HU-35: implementa LlmClient con el cliente Feign generado desde llm-chat.yaml. */
@Service
public class ProveedorLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(ProveedorLlmClient.class);

    private final LlmApiClient api;
    private final LlmProperties props;

    public ProveedorLlmClient(LlmApiClient api, LlmProperties props) {
        this.api = api;
        this.props = props;
    }

    @Override
    public String generar(Prompt prompt) {
        ChatCompletionRequest peticion = new ChatCompletionRequest()
                .model(props.modelo())
                .addMessagesItem(new ChatMessage().role("system").content(prompt.sistema()))
                .addMessagesItem(new ChatMessage().role("user").content(prompt.usuario()));

        ResponseEntity<ChatCompletionResponse> respuesta;
        try {
            respuesta = api.crearChatCompletion(peticion);
        } catch (FeignException e) {
            throw traducir(e);
        }

        String texto = extraerTexto(respuesta.getBody());
        if (texto == null || texto.isBlank()) {
            throw new LlmNoDisponibleException("El LLM devolvió una respuesta vacía");
        }
        return texto.strip();
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
