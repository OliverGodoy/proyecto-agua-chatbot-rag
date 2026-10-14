package com.oficinaagua.chatbot_api.llm;

/** El proveedor del LLM no está configurado o no respondió (503 LLM_UNAVAILABLE). */
public class LlmNoDisponibleException extends RuntimeException {

    public LlmNoDisponibleException(String mensaje) {
        super(mensaje);
    }

    public LlmNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
