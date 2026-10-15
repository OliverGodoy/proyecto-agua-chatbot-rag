package com.oficinaagua.chatbot_api.chat;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * HU-34: parámetros de la búsqueda por similitud.
 *
 * @param topK            cantidad máxima de fragmentos que se recuperan
 * @param similitudMinima similitud coseno mínima (0 a 1) para considerar un fragmento relevante
 */
@ConfigurationProperties(prefix = "chatbot.chat")
public record ChatProperties(Integer topK, Double similitudMinima) {

    public ChatProperties {
        if (topK == null) {
            topK = 4;
        }
        if (similitudMinima == null) {
            similitudMinima = 0.30;
        }
        if (topK < 1 || topK > 20) {
            throw new IllegalArgumentException("CHAT_TOP_K debe estar entre 1 y 20");
        }
        if (similitudMinima < 0 || similitudMinima > 1) {
            throw new IllegalArgumentException("CHAT_SIMILITUD_MINIMA debe estar entre 0 y 1");
        }
    }
}
