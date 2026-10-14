package com.oficinaagua.chatbot_api.llm;

/**
 * Contrato entre HU-34 (arma el prompt) y HU-35 (llama al LLM externo).
 * Las implementaciones de HU-35 deben lanzar LlmNoDisponibleException o
 * LlmTimeoutException cuando el proveedor falle, para responder 503 o 504.
 */
public interface LlmClient {

    /** Genera la respuesta final a partir del prompt armado con el contexto. */
    String generar(Prompt prompt);
}
