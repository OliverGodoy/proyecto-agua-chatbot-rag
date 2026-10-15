package com.oficinaagua.chatbot_api.chat;

/** HU-34: fragmento recuperado de la Vector database con su similitud coseno (0 a 1). */
public record FragmentoEncontrado(String documento, String seccion, String contenido, double similitud) {
}
