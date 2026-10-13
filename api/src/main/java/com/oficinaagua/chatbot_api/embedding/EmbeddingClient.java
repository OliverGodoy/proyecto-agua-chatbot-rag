package com.oficinaagua.chatbot_api.embedding;

import java.util.List;

/**
 * HU-33: convierte textos en vectores (embeddings).
 * Lo usa la ingesta para los fragmentos y HU-34 para la pregunta del usuario,
 * por eso ambos deben usar el mismo proveedor y modelo.
 */
public interface EmbeddingClient {

    /** Devuelve un vector por cada texto, en el mismo orden. */
    List<float[]> embeber(List<String> textos);

    default float[] embeber(String texto) {
        return embeber(List.of(texto)).get(0);
    }
}
