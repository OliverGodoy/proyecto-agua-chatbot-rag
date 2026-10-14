package com.oficinaagua.chatbot_api.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.oficinaagua.chatbot_api.llm.Prompt;

class PromptBuilderTest {

    private final PromptBuilder builder = new PromptBuilder();

    @Test
    void incluyeLaInstruccionDeNoInventar() {
        Prompt prompt = builder.construir("¿Dónde pago?", List.of());

        assertThat(prompt.sistema())
                .contains("Responde únicamente con la información del CONTEXTO")
                .contains("No inventes");
    }

    @Test
    void incluyeElContextoNumeradoConSuOrigenYLaPregunta() {
        List<FragmentoEncontrado> fragmentos = List.of(
                new FragmentoEncontrado("como-pagar.md", "¿Dónde puedo pagar?", "  En agencias bancarias.  ", 0.8),
                new FragmentoEncontrado("tarifas.md", null, "La cuota mínima es mensual.", 0.6));

        Prompt prompt = builder.construir("¿Dónde pago mi recibo?", fragmentos);

        assertThat(prompt.usuario())
                .startsWith("CONTEXTO:")
                .contains("[1] como-pagar.md — ¿Dónde puedo pagar?\nEn agencias bancarias.")
                .contains("[2] tarifas.md\nLa cuota mínima es mensual.")
                .endsWith("PREGUNTA DEL USUARIO:\n¿Dónde pago mi recibo?");
    }
}
