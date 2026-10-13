package com.oficinaagua.chatbot_api.embedding;

import java.util.List;

/** HU-33: utilidades para convertir vectores. */
public final class Vectores {

    private Vectores() {
    }

    static float[] aFloat(List<Double> valores) {
        if (valores == null || valores.isEmpty()) {
            throw new EmbeddingException("El proveedor devolvió un embedding vacío");
        }
        float[] vector = new float[valores.size()];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = valores.get(i).floatValue();
        }
        return vector;
    }

    /** Formato que entiende pgvector: [0.1,0.2,0.3] */
    public static String aLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 10).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }
}
