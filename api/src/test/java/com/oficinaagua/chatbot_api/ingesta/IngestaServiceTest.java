package com.oficinaagua.chatbot_api.ingesta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.oficinaagua.chatbot_api.embedding.EmbeddingClient;
import com.oficinaagua.chatbot_api.embedding.EmbeddingException;
import com.oficinaagua.chatbot_api.embedding.EmbeddingProperties;

class IngestaServiceTest {

    private final ChunkingService chunking = mock(ChunkingService.class);
    private final EmbeddingClient embeddings = mock(EmbeddingClient.class);
    private final FragmentoRepository repositorio = mock(FragmentoRepository.class);
    private final IngestaService servicio = new IngestaService(chunking, embeddings, repositorio,
            new EmbeddingProperties("openai", null, null, "sk", 3, 30));

    private final Fragmento existente = new Fragmento("tarifas.md", "Cuota mínima", 0, "texto uno", "aaa");
    private final Fragmento nuevo = new Fragmento("tarifas.md", "Excedente", 1, "texto dos", "bbb");

    @Test
    void soloGeneraEmbeddingsDeLosFragmentosNuevos() throws IOException {
        float[] vector = {0.1f, 0.2f, 0.3f};
        when(chunking.dividirDirectorio()).thenReturn(List.of(existente, nuevo));
        when(repositorio.hashesDe("tarifas.md")).thenReturn(Set.of("aaa"));
        when(embeddings.embeber(List.of("texto dos"))).thenReturn(List.of(vector));
        when(repositorio.insertar(nuevo, vector)).thenReturn(1);

        ResultadoIngesta resultado = servicio.ejecutar();

        assertThat(resultado.nuevos()).isEqualTo(1);
        assertThat(resultado.existentes()).isEqualTo(1);
        verify(repositorio, never()).insertar(existente, vector);
        verify(repositorio).eliminarObsoletos("tarifas.md", List.of("aaa", "bbb"));
        verify(repositorio).eliminarDocumentosAusentes(Set.of("tarifas.md"));
    }

    @Test
    void reejecutarSinCambiosNoLlamaAlProveedorNiDuplica() throws IOException {
        when(chunking.dividirDirectorio()).thenReturn(List.of(existente, nuevo));
        when(repositorio.hashesDe("tarifas.md")).thenReturn(Set.of("aaa", "bbb"));

        ResultadoIngesta resultado = servicio.ejecutar();

        assertThat(resultado.nuevos()).isZero();
        assertThat(resultado.existentes()).isEqualTo(2);
        verify(embeddings, never()).embeber(anyList());
        verify(repositorio, never()).insertar(any(), any());
    }

    @Test
    void rechazaVectoresDeOtraDimension() throws IOException {
        when(chunking.dividirDirectorio()).thenReturn(List.of(nuevo));
        when(repositorio.hashesDe("tarifas.md")).thenReturn(Set.of());
        when(embeddings.embeber(List.of("texto dos"))).thenReturn(List.of(new float[] {0.1f, 0.2f}));

        assertThatThrownBy(servicio::ejecutar)
                .isInstanceOf(EmbeddingException.class)
                .hasMessageContaining("EMBEDDING_DIMENSION");
        verify(repositorio, never()).insertar(any(), any());
    }

    @Test
    void sinDocumentosNoBorraNada() throws IOException {
        when(chunking.dividirDirectorio()).thenReturn(List.of());

        assertThatThrownBy(servicio::ejecutar).isInstanceOf(IllegalStateException.class);
        verify(repositorio, never()).eliminarDocumentosAusentes(any());
        verify(repositorio, never()).eliminarObsoletos(anyString(), any());
    }
}
