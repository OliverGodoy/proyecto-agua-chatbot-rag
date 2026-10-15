package com.oficinaagua.chatbot_api.llm;

/**
 * Prompt para el LLM.
 *
 * @param sistema instrucciones fijas del asistente (rol "system")
 * @param usuario contexto recuperado de la Vector database más la pregunta (rol "user")
 */
public record Prompt(String sistema, String usuario) {
}
