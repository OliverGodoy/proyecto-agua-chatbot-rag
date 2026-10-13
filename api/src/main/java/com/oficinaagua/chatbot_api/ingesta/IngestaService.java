package com.oficinaagua.chatbot_api.ingesta;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.oficinaagua.chatbot_api.embedding.EmbeddingClient;
import com.oficinaagua.chatbot_api.embedding.EmbeddingException;
import com.oficinaagua.chatbot_api.embedding.EmbeddingProperties;

/**
 * HU-33: genera el embedding de cada chunk y lo guarda en la Vector database.
 * Es idempotente: solo pide embeddings de los fragmentos nuevos, no duplica los
 * existentes y elimina los que ya no están en el contenido actual.
 */
@Service
public class IngestaService {

    private static final Logger log = LoggerFactory.getLogger(IngestaService.class);
    private static final int TAMANO_LOTE = 100;

    private final ChunkingService chunking;
    private final EmbeddingClient embeddings;
    private final FragmentoRepository repositorio;
    private final EmbeddingProperties props;

    public IngestaService(ChunkingService chunking, EmbeddingClient embeddings,
                          FragmentoRepository repositorio, EmbeddingProperties props) {
        this.chunking = chunking;
        this.embeddings = embeddings;
        this.repositorio = repositorio;
        this.props = props;
    }

    public ResultadoIngesta ejecutar() throws IOException {
        List<Fragmento> fragmentos = chunking.dividirDirectorio();
        if (fragmentos.isEmpty()) {
            // Evita vaciar la Vector database por una ruta de contenido mal configurada.
            throw new IllegalStateException("No se encontraron documentos para ingestar");
        }

        Map<String, List<Fragmento>> porDocumento = fragmentos.stream()
                .collect(Collectors.groupingBy(Fragmento::documento, LinkedHashMap::new, Collectors.toList()));

        int nuevos = 0;
        int existentes = 0;
        int eliminados = 0;
        for (Map.Entry<String, List<Fragmento>> entrada : porDocumento.entrySet()) {
            String documento = entrada.getKey();
            Set<String> guardados = repositorio.hashesDe(documento);

            Map<String, Fragmento> vigentes = new LinkedHashMap<>();
            entrada.getValue().forEach(f -> vigentes.putIfAbsent(f.hashContenido(), f));

            List<Fragmento> pendientes = vigentes.values().stream()
                    .filter(f -> !guardados.contains(f.hashContenido()))
                    .toList();
            existentes += vigentes.size() - pendientes.size();

            for (int inicio = 0; inicio < pendientes.size(); inicio += TAMANO_LOTE) {
                List<Fragmento> lote = pendientes.subList(inicio, Math.min(inicio + TAMANO_LOTE, pendientes.size()));
                List<float[]> vectores = embeddings.embeber(lote.stream().map(Fragmento::contenido).toList());
                for (int i = 0; i < lote.size(); i++) {
                    validarDimension(vectores.get(i));
                    nuevos += repositorio.insertar(lote.get(i), vectores.get(i));
                }
            }
            eliminados += repositorio.eliminarObsoletos(documento, new ArrayList<>(vigentes.keySet()));
        }
        eliminados += repositorio.eliminarDocumentosAusentes(porDocumento.keySet());

        ResultadoIngesta resultado = new ResultadoIngesta(porDocumento.size(), nuevos, existentes, eliminados);
        log.info("Ingesta terminada: {} documentos, {} nuevos, {} sin cambios, {} eliminados",
                resultado.documentos(), resultado.nuevos(), resultado.existentes(), resultado.eliminados());
        return resultado;
    }

    private void validarDimension(float[] vector) {
        if (vector.length != props.dimension()) {
            throw new EmbeddingException("El modelo devolvió vectores de " + vector.length
                    + " dimensiones, pero EMBEDDING_DIMENSION es " + props.dimension());
        }
    }
}
