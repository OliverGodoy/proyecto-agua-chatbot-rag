package com.oficinaagua.chatbot_api.ingesta;

public record Fragmento(String documento, String seccion, int indice,
                        String contenido, String hashContenido) {}
