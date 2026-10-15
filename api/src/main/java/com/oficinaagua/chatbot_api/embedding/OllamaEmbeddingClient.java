package com.oficinaagua.chatbot_api.embedding;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** HU-33: embeddings con Ollama local (POST /api/embed). No necesita API key. */
public class OllamaEmbeddingClient implements EmbeddingClient {

    private final RestClient http;
    private final EmbeddingProperties props;

    public OllamaEmbeddingClient(RestClient.Builder builder, EmbeddingProperties props) {
        this.http = builder.build();
        this.props = props;
    }

    @Override
    public List<float[]> embeber(List<String> textos) {
        if (textos.isEmpty()) {
            return List.of();
        }
        Respuesta respuesta;
        try {
            respuesta = http.post()
                    .uri("/api/embed")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("model", props.modelo(), "input", textos))
                    .retrieve()
                    .body(Respuesta.class);
        } catch (RestClientException e) {
            throw new EmbeddingException("No se pudieron obtener embeddings de Ollama: " + e.getMessage(), e);
        }
        if (respuesta == null || respuesta.embeddings() == null || respuesta.embeddings().size() != textos.size()) {
            throw new EmbeddingException("Ollama devolvió una cantidad de embeddings distinta a la enviada");
        }
        return respuesta.embeddings().stream().map(Vectores::aFloat).toList();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Respuesta(List<List<Double>> embeddings) {
    }
}
