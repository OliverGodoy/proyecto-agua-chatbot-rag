package com.oficinaagua.chatbot_api.ingesta;

/** HU-33: resumen de una ejecución de la ingesta. */
public record ResultadoIngesta(int documentos, int nuevos, int existentes, int eliminados) {
}
