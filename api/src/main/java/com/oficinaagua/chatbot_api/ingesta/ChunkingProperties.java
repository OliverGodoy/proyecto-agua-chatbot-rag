package com.oficinaagua.chatbot_api.ingesta;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "chatbot.chunking")
public record ChunkingProperties(
        @DefaultValue("../content") String ruta,
        @DefaultValue("800") int maxCaracteres,
        @DefaultValue("100") int solapamiento) {

    public ChunkingProperties {
        if (maxCaracteres <= 0 || solapamiento < 0 || solapamiento >= maxCaracteres) {
            throw new IllegalArgumentException("El solapamiento debe ser menor que maxCaracteres");
        }
    }
}
