package com.oficinaagua.chatbot_api.chat;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.oficinaagua.chatbot_api.embedding.Vectores;

/**
 * HU-34: búsqueda por similitud en pgvector.
 * El operador <=> es la distancia coseno, que usa el índice HNSW de la tabla.
 */
@Repository
public class BusquedaRepository {

    private final JdbcTemplate jdbc;

    public BusquedaRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<FragmentoEncontrado> buscarSimilares(float[] vectorPregunta, int limite) {
        String vector = Vectores.aLiteral(vectorPregunta);
        return jdbc.query("""
                SELECT documento, seccion, contenido, 1 - (embedding <=> ?::vector) AS similitud
                FROM fragmentos
                WHERE embedding IS NOT NULL
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """,
                (fila, numero) -> new FragmentoEncontrado(
                        fila.getString("documento"),
                        fila.getString("seccion"),
                        fila.getString("contenido"),
                        fila.getDouble("similitud")),
                vector, vector, limite);
    }
}
