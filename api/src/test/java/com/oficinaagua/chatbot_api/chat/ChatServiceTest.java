package com.oficinaagua.chatbot_api.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import com.oficinaagua.chatbot_api.api.model.ChatResponse;
import com.oficinaagua.chatbot_api.api.model.Source;
import com.oficinaagua.chatbot_api.embedding.EmbeddingClient;
import com.oficinaagua.chatbot_api.llm.LlmClient;
import com.oficinaagua.chatbot_api.llm.LlmNoDisponibleException;
import com.oficinaagua.chatbot_api.llm.Prompt;
import com.oficinaagua.chatbot_api.registro.RegistroInteraccionService;

class ChatServiceTest {

    private static final float[] VECTOR = {0.1f, 0.2f, 0.3f};

    private final EmbeddingClient embeddings = mock(EmbeddingClient.class);
    private final BusquedaRepository busqueda = mock(BusquedaRepository.class);
    private final LlmClient llm = mock(LlmClient.class);
    private final RegistroInteraccionService registro = mock(RegistroInteraccionService.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<LlmClient> proveedorLlm = mock(ObjectProvider.class);

    private ChatService servicio;

    @BeforeEach
    void preparar() {
        when(proveedorLlm.getIfAvailable()).thenReturn(llm);
        when(embeddings.embeber("¿Cómo pago?")).thenReturn(VECTOR);
        servicio = new ChatService(embeddings, busqueda, new PromptBuilder(), proveedorLlm, registro,
                new ChatProperties(4, 0.30));
    }

    @Test
    void sinFragmentosRelevantesRespondeQueNoTieneInformacionSinLlamarAlLlm() {
        when(busqueda.buscarSimilares(VECTOR, 4)).thenReturn(List.of(
                new FragmentoEncontrado("fugas.md", "Fugas", "texto", 0.12)));

        ChatResponse respuesta = servicio.responder("¿Cómo pago?");

        assertThat(respuesta.getAnswer()).isEqualTo(ChatService.SIN_INFORMACION);
        assertThat(respuesta.getSources()).isEmpty();
        verifyNoInteractions(llm);
        verify(registro).registrar("¿Cómo pago?", ChatService.SIN_INFORMACION, List.of());
    }

    @Test
    void conFragmentosRelevantesArmaElPromptYDevuelveLasFuentesSinRepetir() {
        when(busqueda.buscarSimilares(VECTOR, 4)).thenReturn(List.of(
                new FragmentoEncontrado("como-pagar.md", "¿Dónde puedo pagar?", "En agencias.", 0.71),
                new FragmentoEncontrado("como-pagar.md", "¿Dónde puedo pagar?", "También en línea.", 0.65),
                new FragmentoEncontrado("tarifas.md", "Cuota mínima", "Cuota mensual.", 0.41),
                new FragmentoEncontrado("fugas.md", "Fugas", "Llame a la oficina.", 0.10)));
        when(llm.generar(any(Prompt.class))).thenReturn("Puedes pagar en agencias o en línea.");

        ChatResponse respuesta = servicio.responder("  ¿Cómo pago?  ");

        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(llm).generar(prompt.capture());
        assertThat(prompt.getValue().usuario())
                .contains("En agencias.", "También en línea.", "Cuota mensual.")
                .doesNotContain("Llame a la oficina.")
                .endsWith("¿Cómo pago?");

        assertThat(respuesta.getAnswer()).isEqualTo("Puedes pagar en agencias o en línea.");
        assertThat(respuesta.getSources()).extracting(Source::getDocument, Source::getSection)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("como-pagar.md", "¿Dónde puedo pagar?"),
                        org.assertj.core.groups.Tuple.tuple("tarifas.md", "Cuota mínima"));
        verify(registro).registrar(eq("¿Cómo pago?"), eq("Puedes pagar en agencias o en línea."), anyList());
    }

    @Test
    void unaPreguntaConSoloEspaciosEsInvalida() {
        assertThatThrownBy(() -> servicio.responder("   "))
                .isInstanceOf(PreguntaInvalidaException.class);
        verifyNoInteractions(embeddings, busqueda, llm);
    }

    @Test
    void sinProveedorDeLlmConfiguradoRespondeNoDisponible() {
        when(proveedorLlm.getIfAvailable()).thenReturn(null);
        when(busqueda.buscarSimilares(VECTOR, 4)).thenReturn(List.of(
                new FragmentoEncontrado("tarifas.md", "Cuota mínima", "Cuota mensual.", 0.55)));

        assertThatThrownBy(() -> servicio.responder("¿Cómo pago?"))
                .isInstanceOf(LlmNoDisponibleException.class);
        verify(registro, never()).registrar(anyString(), anyString(), anyList());
    }
}
