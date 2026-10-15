package com.oficinaagua.chatbot_api.chat;

/** HU-34: la pregunta está vacía o solo tiene espacios (400 INVALID_QUESTION). */
public class PreguntaInvalidaException extends RuntimeException {

    public PreguntaInvalidaException(String mensaje) {
        super(mensaje);
    }
}
