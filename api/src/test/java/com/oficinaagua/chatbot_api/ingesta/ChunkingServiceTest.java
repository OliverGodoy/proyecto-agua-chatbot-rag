package com.oficinaagua.chatbot_api.ingesta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChunkingServiceTest {

    private static final String DOC = """
            # Tarifas
            ## Primera pregunta
            Respuesta corta uno.
            ## Segunda pregunta
            Respuesta corta dos.
            """;

    private ChunkingService servicio(int max, int solape) {
        return new ChunkingService(new ChunkingProperties("../content", max, solape));
    }

    @Test
    void unaSeccionCortaEsUnFragmentoConSuOrigen() {
        List<Fragmento> r = servicio(800, 100).dividirDocumento("tarifas.md", DOC);
        assertThat(r).hasSize(2);
        assertThat(r).extracting(Fragmento::documento).containsOnly("tarifas.md");
        assertThat(r).extracting(Fragmento::seccion).containsExactly("Primera pregunta", "Segunda pregunta");
        assertThat(r).extracting(Fragmento::indice).containsExactly(0, 1);
        assertThat(r.get(0).contenido()).contains("Tarifas").contains("Respuesta corta uno.");
    }

    @Test
    void unaSeccionLargaSeDivideRespetandoElMaximo() {
        String doc = "# Doc\n## Larga\n" + "palabra ".repeat(200) + "\n";
        List<Fragmento> r = servicio(300, 50).dividirDocumento("doc.md", doc);
        int encabezado = "Doc - Larga\n\n".length();
        assertThat(r.size()).isGreaterThan(1);
        assertThat(r).allSatisfy(f -> assertThat(f.contenido().length()).isLessThanOrEqualTo(300 + encabezado));
        assertThat(r).extracting(Fragmento::seccion).containsOnly("Larga");
    }

    @Test
    void elHashEsEstableYDependeDelContenido() {
        List<Fragmento> a = servicio(800, 100).dividirDocumento("tarifas.md", DOC);
        List<Fragmento> b = servicio(800, 100).dividirDocumento("tarifas.md", DOC);
        assertThat(a).isEqualTo(b);
        assertThat(a.get(0).hashContenido()).matches("[0-9a-f]{64}").isNotEqualTo(a.get(1).hashContenido());
    }

    @Test
    void elSolapamientoDebeSerMenorQueElMaximo() {
        assertThatThrownBy(() -> new ChunkingProperties("../content", 100, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void divideElContenidoRealDelDominio() throws IOException {
        List<Fragmento> r = servicio(800, 100).dividirDirectorio();
        assertThat(r).extracting(Fragmento::documento)
                .contains("tarifas.md", "calculo-consumo.md", "como-pagar.md", "fugas.md");
        assertThat(r).allSatisfy(f -> {
            assertThat(f.contenido()).isNotBlank();
            assertThat(f.seccion()).isNotBlank();
            assertThat(f.hashContenido()).matches("[0-9a-f]{64}");
        });
        assertThat(r.stream().map(f -> f.documento() + f.hashContenido()).distinct().count())
                .isEqualTo(r.size());
    }
}
