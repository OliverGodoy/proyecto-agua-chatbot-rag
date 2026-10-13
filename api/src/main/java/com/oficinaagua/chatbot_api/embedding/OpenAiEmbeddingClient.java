package com.oficinaagua.chatbot_api.embedding;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** HU-33: embeddings con la API de OpenAI (POST /embeddings). */
public class OpenAiEmbeddingClient implements EmbeddingClient {

    private final RestClient http;
    private final EmbeddingProperties props;

    public OpenAiEmbeddingClient(RestClient.Builder builder, EmbeddingProperties props) {
        this.http = builder.build();
        this.props = props;
    }

    @Override
    public List<float[]> embeber(List<String> textos) {
        if (textos.isEmpty()) {
            return List.of();
        }
        if (props.apiKey().isEmpty()) {
            throw new EmbeddingException("Falta la API key: configure EMBEDDING_API_KEY o LLM_API_KEY");
        }
        Respuesta respuesta;
        try {
            respuesta = http.post()
                    .uri("/embeddings")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + props.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("model", props.modelo(), "input", textos))
                    .retrieve()
                    .body(Respuesta.class);
        } catch (RestClientException e) {
            throw new EmbeddingException("No se pudieron obtener embeddings de OpenAI: " + e.getMessage(), e);
        }
        if (respuesta == null || respuesta.data() == null || respuesta.data().size() != textos.size()) {
            throw new EmbeddingException("OpenAI devolvió una cantidad de embeddings distinta a la enviada");
        }
        return respuesta.data().stream()
                .sorted(Comparator.comparingInt(Dato::index))
                .map(dato -> Vectores.aFloat(dato.embedding()))
                .toList();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Respuesta(List<Dato> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Dato(int index, List<Double> embedding) {
    }
}
