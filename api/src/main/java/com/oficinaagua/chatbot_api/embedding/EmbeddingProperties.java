package com.oficinaagua.chatbot_api.embedding;

import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * HU-33: configuración del proveedor de embeddings, tomada de variables de entorno.
 * Si una variable viene vacía se usa el valor por defecto del proveedor elegido.
 */
@ConfigurationProperties(prefix = "chatbot.embedding")
public record EmbeddingProperties(
        String proveedor,
        String modelo,
        String url,
        String apiKey,
        Integer dimension,
        Integer timeoutSegundos) {

    public static final String OPENAI = "openai";
    public static final String OLLAMA = "ollama";

    public EmbeddingProperties {
        proveedor = vacio(proveedor) ? OPENAI : proveedor.trim().toLowerCase(Locale.ROOT);
        boolean ollama = OLLAMA.equals(proveedor);
        if (vacio(modelo)) {
            modelo = ollama ? "nomic-embed-text" : "text-embedding-3-small";
        }
        if (vacio(url)) {
            url = ollama ? "http://localhost:11434" : "https://api.openai.com/v1";
        }
        apiKey = apiKey == null ? "" : apiKey.trim();
        if (dimension == null) {
            dimension = 1536;
        }
        if (timeoutSegundos == null) {
            timeoutSegundos = 30;
        }
        if (dimension <= 0 || timeoutSegundos <= 0) {
            throw new IllegalArgumentException("La dimensión y el timeout deben ser mayores que cero");
        }
    }

    private static boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }
}
