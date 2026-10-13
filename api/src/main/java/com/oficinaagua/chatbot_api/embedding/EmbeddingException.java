package com.oficinaagua.chatbot_api.embedding;

/** HU-33: el proveedor de embeddings no respondió o respondió algo inesperado. */
public class EmbeddingException extends RuntimeException {

    public EmbeddingException(String mensaje) {
        super(mensaje);
    }

    public EmbeddingException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
