package com.oficinaagua.chatbot_api.embedding;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** HU-33: elige la implementación según EMBEDDING_PROVIDER. */
@Configuration
public class EmbeddingConfig {

    @Bean
    public EmbeddingClient embeddingClient(EmbeddingProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(props.timeoutSegundos()));
        RestClient.Builder builder = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(props.url());

        return switch (props.proveedor()) {
            case EmbeddingProperties.OPENAI -> new OpenAiEmbeddingClient(builder, props);
            case EmbeddingProperties.OLLAMA -> new OllamaEmbeddingClient(builder, props);
            default -> throw new IllegalStateException(
                    "EMBEDDING_PROVIDER no soportado: " + props.proveedor() + " (use openai u ollama)");
        };
    }
}
