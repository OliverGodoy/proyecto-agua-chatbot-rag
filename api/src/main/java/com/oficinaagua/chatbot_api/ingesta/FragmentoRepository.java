package com.oficinaagua.chatbot_api.ingesta;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.oficinaagua.chatbot_api.embedding.Vectores;

/**
 * HU-33: acceso a la tabla fragmentos de la Vector database.
 * Todas las consultas usan parámetros (prepared statements).
 */
@Repository
public class FragmentoRepository {

    /** Separador seguro: no puede aparecer en un nombre de archivo ni en un hash hexadecimal. */
    private static final String SEPARADOR = "/";

    private final JdbcTemplate jdbc;

    public FragmentoRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Set<String> hashesDe(String documento) {
        return new HashSet<>(jdbc.queryForList(
                "SELECT hash_contenido FROM fragmentos WHERE documento = ?", String.class, documento));
    }

    /** Devuelve 1 si se insertó, 0 si ya existía (no duplica). */
    public int insertar(Fragmento fragmento, float[] embedding) {
        return jdbc.update("""
                INSERT INTO fragmentos (documento, seccion, contenido, hash_contenido, embedding, metadata)
                VALUES (?, ?, ?, ?, ?::vector, jsonb_build_object('indice', ?::int))
                ON CONFLICT (documento, hash_contenido) DO NOTHING
                """,
                fragmento.documento(), fragmento.seccion(), fragmento.contenido(),
                fragmento.hashContenido(), Vectores.aLiteral(embedding), fragmento.indice());
    }

    /** Borra los fragmentos de un documento que ya no existen en su versión actual. */
    public int eliminarObsoletos(String documento, Collection<String> hashesVigentes) {
        return jdbc.update("""
                DELETE FROM fragmentos
                WHERE documento = ? AND hash_contenido::text <> ALL (string_to_array(?, '/'))
                """, documento, String.join(SEPARADOR, hashesVigentes));
    }

    /** Borra los fragmentos de documentos que ya no están en la carpeta de contenido. */
    public int eliminarDocumentosAusentes(Collection<String> documentosVigentes) {
        return jdbc.update("""
                DELETE FROM fragmentos
                WHERE documento <> ALL (string_to_array(?, '/'))
                """, String.join(SEPARADOR, documentosVigentes));
    }
}
