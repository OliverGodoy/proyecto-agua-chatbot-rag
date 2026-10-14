package com.oficinaagua.chatbot_api.llm;

/** El LLM tardó más que el tiempo máximo permitido (504 LLM_TIMEOUT). */
public class LlmTimeoutException extends RuntimeException {

    public LlmTimeoutException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
