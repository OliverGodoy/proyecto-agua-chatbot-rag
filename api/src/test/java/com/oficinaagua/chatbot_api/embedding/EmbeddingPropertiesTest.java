package com.oficinaagua.chatbot_api.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmbeddingPropertiesTest {

    @Test
    void variablesVaciasUsanLosValoresDeOpenAi() {
        EmbeddingProperties props = new EmbeddingProperties("", "", "", null, null, null);

        assertThat(props.proveedor()).isEqualTo("openai");
        assertThat(props.modelo()).isEqualTo("text-embedding-3-small");
        assertThat(props.url()).isEqualTo("https://api.openai.com/v1");
        assertThat(props.dimension()).isEqualTo(1536);
    }

    @Test
    void ollamaUsaSusPropiosValores() {
        EmbeddingProperties props = new EmbeddingProperties("Ollama", null, null, null, 768, null);

        assertThat(props.proveedor()).isEqualTo("ollama");
        assertThat(props.modelo()).isEqualTo("nomic-embed-text");
        assertThat(props.url()).isEqualTo("http://localhost:11434");
    }

    @Test
    void elVectorSeEscribeEnFormatoPgvector() {
        assertThat(Vectores.aLiteral(new float[] {0.5f, -1.0f})).isEqualTo("[0.5,-1.0]");
    }
}
