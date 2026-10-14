package com.oficinaagua.chatbot_api.chat;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.oficinaagua.chatbot_api.api.model.ChatResponse;
import com.oficinaagua.chatbot_api.api.model.Source;
import com.oficinaagua.chatbot_api.llm.LlmNoDisponibleException;
import com.oficinaagua.chatbot_api.llm.LlmTimeoutException;

class ChatControllerTest {

    private final ChatService servicio = mock(ChatService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ChatController(servicio))
            .setControllerAdvice(new ManejadorErrores())
            .build();

    private void preguntar(String json, int estadoEsperado, String campo, String valor) throws Exception {
        mvc.perform(post("/api/v1/chat").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().is(estadoEsperado))
                .andExpect(jsonPath(campo).value(valor));
    }

    @Test
    void respondeConLaRespuestaYSusFuentes() throws Exception {
        when(servicio.responder("Como pago?")).thenReturn(new ChatResponse()
                .answer("En agencias bancarias.")
                .sources(List.of(new Source().document("como-pagar.md").section("Pagos"))));

        preguntar("{\"question\":\"Como pago?\"}", 200, "$.sources[0].document", "como-pagar.md");
    }

    @Test
    void unaPreguntaVaciaDevuelve400() throws Exception {
        preguntar("{\"question\":\"\"}", 400, "$.code", "INVALID_QUESTION");
    }

    @Test
    void unaPreguntaDeMasDe500CaracteresDevuelve400() throws Exception {
        preguntar("{\"question\":\"" + "a".repeat(501) + "\"}", 400, "$.code", "INVALID_QUESTION");
    }

    @Test
    void siElLlmNoEstaDisponibleDevuelve503() throws Exception {
        when(servicio.responder(anyString())).thenThrow(new LlmNoDisponibleException("caido"));

        preguntar("{\"question\":\"Hola\"}", 503, "$.code", "LLM_UNAVAILABLE");
    }

    @Test
    void siElLlmTardaDemasiadoDevuelve504() throws Exception {
        when(servicio.responder(anyString())).thenThrow(new LlmTimeoutException("lento", null));

        preguntar("{\"question\":\"Hola\"}", 504, "$.code", "LLM_TIMEOUT");
    }

    @Test
    void unErrorInesperadoNoMuestraDetallesInternos() throws Exception {
        when(servicio.responder(anyString())).thenThrow(new IllegalStateException("password=secreto"));

        preguntar("{\"question\":\"Hola\"}", 500, "$.code", "INTERNAL_ERROR");
    }
}
