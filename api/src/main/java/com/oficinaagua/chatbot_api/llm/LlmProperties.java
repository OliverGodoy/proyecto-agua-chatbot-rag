package com.oficinaagua.chatbot_api.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** HU-35: modelo y API key del LLM. La URL va en la propiedad llmApi.url. */
@ConfigurationProperties(prefix = "chatbot.llm")
public record LlmProperties(String modelo, String apiKey) {

    public LlmProperties {
        modelo = (modelo == null || modelo.isBlank()) ? "gpt-5-mini" : modelo.trim();
        apiKey = apiKey == null ? "" : apiKey.trim();
    }
}
