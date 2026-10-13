package com.oficinaagua.chatbot_api.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OpenAiEmbeddingClientTest {

    private static final String URL = "https://api.openai.com/v1";

    private final RestClient.Builder builder = RestClient.builder().baseUrl(URL);
    private final MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();

    private OpenAiEmbeddingClient cliente(String apiKey) {
        return new OpenAiEmbeddingClient(builder,
                new EmbeddingProperties("openai", null, URL, apiKey, 2, 30));
    }

    @Test
    void enviaModeloYApiKeyYRespetaElOrden() {
        OpenAiEmbeddingClient cliente = cliente("sk-prueba");
        servidor.expect(requestTo(URL + "/embeddings"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer sk-prueba"))
                .andExpect(jsonPath("$.model").value("text-embedding-3-small"))
                .andExpect(jsonPath("$.input[1]").value("segundo"))
                .andRespond(withSuccess("""
                        {"object":"list","model":"text-embedding-3-small",
                         "data":[{"object":"embedding","index":1,"embedding":[0.3,0.4]},
                                 {"object":"embedding","index":0,"embedding":[0.1,0.2]}]}
                        """, MediaType.APPLICATION_JSON));

        List<float[]> vectores = cliente.embeber(List.of("primero", "segundo"));

        assertThat(vectores.get(0)).containsExactly(0.1f, 0.2f);
        assertThat(vectores.get(1)).containsExactly(0.3f, 0.4f);
        servidor.verify();
    }

    @Test
    void sinApiKeyDaUnErrorClaro() {
        assertThatThrownBy(() -> cliente("").embeber(List.of("hola")))
                .isInstanceOf(EmbeddingException.class)
                .hasMessageContaining("API key");
    }

    @Test
    void unErrorDelProveedorSeConvierteEnEmbeddingException() {
        OpenAiEmbeddingClient cliente = cliente("sk-prueba");
        servidor.expect(requestTo(URL + "/embeddings")).andRespond(withServerError());

        assertThatThrownBy(() -> cliente.embeber(List.of("hola")))
                .isInstanceOf(EmbeddingException.class);
    }
}
