package com.oficinaagua.chatbot_api.chat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.oficinaagua.chatbot_api.api.model.ChatResponse;
import com.oficinaagua.chatbot_api.api.model.Source;
import com.oficinaagua.chatbot_api.embedding.EmbeddingClient;
import com.oficinaagua.chatbot_api.llm.LlmClient;
import com.oficinaagua.chatbot_api.llm.LlmNoDisponibleException;
import com.oficinaagua.chatbot_api.registro.RegistroInteraccionService;

/**
 * HU-34: flujo RAG de una pregunta.
 * Embedding de la pregunta → top-k en pgvector → filtro por similitud mínima →
 * prompt con contexto → LLM (HU-35) → respuesta con fuentes → registro (HU-38).
 */
@Service
public class ChatService {

    static final String SIN_INFORMACION = "No tengo información sobre eso en el contenido de la Oficina de Agua. "
            + "Te sugiero comunicarte directamente con la oficina para recibir ayuda.";

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final EmbeddingClient embeddings;
    private final BusquedaRepository busqueda;
    private final PromptBuilder promptBuilder;
    private final ObjectProvider<LlmClient> llm;
    private final RegistroInteraccionService registro;
    private final ChatProperties props;

    public ChatService(EmbeddingClient embeddings, BusquedaRepository busqueda, PromptBuilder promptBuilder,
                       ObjectProvider<LlmClient> llm, RegistroInteraccionService registro, ChatProperties props) {
        this.embeddings = embeddings;
        this.busqueda = busqueda;
        this.promptBuilder = promptBuilder;
        this.llm = llm;
        this.registro = registro;
        this.props = props;
    }

    public ChatResponse responder(String preguntaRecibida) {
        String pregunta = preguntaRecibida == null ? "" : preguntaRecibida.strip();
        if (pregunta.isEmpty()) {
            throw new PreguntaInvalidaException("La pregunta está vacía");
        }

        List<FragmentoEncontrado> encontrados = busqueda.buscarSimilares(embeddings.embeber(pregunta), props.topK());
        List<FragmentoEncontrado> relevantes = encontrados.stream()
                .filter(fragmento -> fragmento.similitud() >= props.similitudMinima())
                .toList();
        log.info("Búsqueda: {} fragmentos relevantes de {} (mejor similitud {})", relevantes.size(),
                encontrados.size(), encontrados.isEmpty() ? "-" : String.format("%.3f", encontrados.get(0).similitud()));

        String respuesta;
        List<Source> fuentes;
        if (relevantes.isEmpty()) {
            // Sin contexto no se llama al LLM: evita respuestas inventadas y costo innecesario.
            respuesta = SIN_INFORMACION;
            fuentes = List.of();
        } else {
            LlmClient cliente = llm.getIfAvailable();
            if (cliente == null) {
                throw new LlmNoDisponibleException("No hay un proveedor de LLM configurado");
            }
            respuesta = cliente.generar(promptBuilder.construir(pregunta, relevantes));
            if (indicaSinInformacion(respuesta)) {
                // El contexto recuperado no tenía la respuesta: no se muestran fuentes sin relación.
                log.info("El LLM indicó que el contexto no contiene la respuesta");
                respuesta = SIN_INFORMACION;
                fuentes = List.of();
            } else {
                fuentes = fuentesDe(relevantes);
            }
        }

        registro.registrar(pregunta, respuesta, fuentes);
        return new ChatResponse().answer(respuesta).sources(fuentes);
    }

    private static boolean indicaSinInformacion(String respuesta) {
        return respuesta.toUpperCase(Locale.ROOT).contains(PromptBuilder.MARCA_SIN_INFORMACION);
    }

    /** Una fuente por documento y sección, en orden de similitud. */
    private static List<Source> fuentesDe(List<FragmentoEncontrado> fragmentos) {
        Map<String, Source> unicas = new LinkedHashMap<>();
        for (FragmentoEncontrado fragmento : fragmentos) {
            unicas.computeIfAbsent(fragmento.documento() + "#" + fragmento.seccion(),
                    clave -> new Source().document(fragmento.documento()).section(fragmento.seccion()));
        }
        return List.copyOf(unicas.values());
    }
}
