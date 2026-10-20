package com.oficinaagua.chatbot_api.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.SocketTimeoutException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import com.oficinaagua.chatbot_api.llm.cliente.LlmApiClient;
import com.oficinaagua.chatbot_api.llm.cliente.model.ChatChoice;
import com.oficinaagua.chatbot_api.llm.cliente.model.ChatCompletionRequest;
import com.oficinaagua.chatbot_api.llm.cliente.model.ChatCompletionResponse;
import com.oficinaagua.chatbot_api.llm.cliente.model.ChatMessage;

import feign.FeignException;

@ExtendWith(MockitoExtension.class)
class ProveedorLlmClientTest {

    @Mock
    private LlmApiClient api;

    private ProveedorLlmClient cliente;

    @BeforeEach
    void preparar() {
        // Sin espera entre intentos para que las pruebas sean rápidas.
        cliente = new ProveedorLlmClient(api, new LlmProperties("modelo-prueba", ""), 0);
    }

    private static ResponseEntity<ChatCompletionResponse> respuesta(String texto) {
        return ResponseEntity.ok(new ChatCompletionResponse().addChoicesItem(
                new ChatChoice().message(new ChatMessage().role("assistant").content(texto))));
    }

    @Test
    void devuelveElTextoYEnviaSistemaYUsuario() {
        when(api.crearChatCompletion(any())).thenReturn(respuesta("  Se paga en la oficina.  "));

        String resultado = cliente.generar(new Prompt("reglas", "pregunta"));

        assertThat(resultado).isEqualTo("Se paga en la oficina.");
        ArgumentCaptor<ChatCompletionRequest> captor = ArgumentCaptor.forClass(ChatCompletionRequest.class);
        verify(api).crearChatCompletion(captor.capture());
        assertThat(captor.getValue().getModel()).isEqualTo("modelo-prueba");
        assertThat(captor.getValue().getMessages()).extracting(ChatMessage::getRole)
                .containsExactly("system", "user");
        assertThat(captor.getValue().getMessages()).extracting(ChatMessage::getContent)
                .containsExactly("reglas", "pregunta");
    }

    @Test
    void unaRespuestaVaciaEsUnFalloDelProveedor() {
        when(api.crearChatCompletion(any())).thenReturn(respuesta(" "));

        assertThatThrownBy(() -> cliente.generar(new Prompt("r", "p")))
                .isInstanceOf(LlmNoDisponibleException.class);
    }

    @Test
    void unErrorHttpSeTraduceANoDisponible() {
        FeignException error = mock(FeignException.class);
        when(error.status()).thenReturn(503);
        when(api.crearChatCompletion(any())).thenThrow(error);

        assertThatThrownBy(() -> cliente.generar(new Prompt("r", "p")))
                .isInstanceOf(LlmNoDisponibleException.class)
                .hasMessageContaining("503");
    }

    @Test
    void unTimeoutSeTraduceATiempoAgotado() {
        FeignException error = mock(FeignException.class);
        when(error.getCause()).thenReturn(new SocketTimeoutException("Read timed out"));
        when(api.crearChatCompletion(any())).thenThrow(error);

        assertThatThrownBy(() -> cliente.generar(new Prompt("r", "p")))
                .isInstanceOf(LlmTimeoutException.class);
    }

    @Test
    void reintentaUnaVezSiElProveedorFallaTemporalmente() {
        FeignException error = mock(FeignException.class);
        when(error.status()).thenReturn(429);
        when(api.crearChatCompletion(any())).thenThrow(error).thenReturn(respuesta("Respuesta tras reintento"));

        assertThat(cliente.generar(new Prompt("r", "p"))).isEqualTo("Respuesta tras reintento");
        verify(api, times(2)).crearChatCompletion(any());
    }

    @Test
    void noReintentaErroresDefinitivos() {
        FeignException error = mock(FeignException.class);
        when(error.status()).thenReturn(400);
        when(api.crearChatCompletion(any())).thenThrow(error);

        assertThatThrownBy(() -> cliente.generar(new Prompt("r", "p")))
                .isInstanceOf(LlmNoDisponibleException.class);
        verify(api, times(1)).crearChatCompletion(any());
    }

    @Test
    void noReintentaLosTimeoutsParaNoDuplicarLaEspera() {
        FeignException error = mock(FeignException.class);
        when(error.getCause()).thenReturn(new SocketTimeoutException("Read timed out"));
        when(api.crearChatCompletion(any())).thenThrow(error);

        assertThatThrownBy(() -> cliente.generar(new Prompt("r", "p")))
                .isInstanceOf(LlmTimeoutException.class);
        verify(api, times(1)).crearChatCompletion(any());
    }
}
