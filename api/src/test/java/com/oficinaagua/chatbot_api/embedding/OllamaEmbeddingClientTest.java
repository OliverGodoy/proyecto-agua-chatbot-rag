package com.oficinaagua.chatbot_api.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OllamaEmbeddingClientTest {

    @Test
    void obtieneUnVectorPorTexto() {
        String url = "http://localhost:11434";
        RestClient.Builder builder = RestClient.builder().baseUrl(url);
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        OllamaEmbeddingClient cliente = new OllamaEmbeddingClient(builder,
                new EmbeddingProperties("ollama", null, url, null, 2, 30));

        servidor.expect(requestTo(url + "/api/embed"))
                .andExpect(jsonPath("$.model").value("nomic-embed-text"))
                .andRespond(withSuccess("""
                        {"model":"nomic-embed-text","embeddings":[[0.5,0.6],[0.7,0.8]]}
                        """, MediaType.APPLICATION_JSON));

        List<float[]> vectores = cliente.embeber(List.of("a", "b"));

        assertThat(vectores).hasSize(2);
        assertThat(vectores.get(1)).containsExactly(0.7f, 0.8f);
        servidor.verify();
    }
}
