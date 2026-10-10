package com.oficinaagua.chatbot_api.registro;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SanitizadorDatosPersonalesTest {

    @Test
    void ocultaCorreo() {
        assertThat(SanitizadorDatosPersonales.limpiar("Escríbanme a karen.prueba@gmail.com por favor"))
                .isEqualTo("Escríbanme a [CORREO] por favor");
    }

    @Test
    void ocultaTelefonoYDpi() {
        assertThat(SanitizadorDatosPersonales.limpiar("Mi teléfono es 5555-1234 y mi DPI 2345 67890 0101"))
                .isEqualTo("Mi teléfono es [NUMERO] y mi DPI [NUMERO]");
    }

    @Test
    void ocultaNit() {
        assertThat(SanitizadorDatosPersonales.limpiar("NIT 1234567-K"))
                .isEqualTo("NIT [NIT]");
    }

    @Test
    void conservaPreguntasNormales() {
        String pregunta = "¿Cuánto pago si consumo 25 metros cúbicos en 2026? La cuota es Q35.00";
        assertThat(SanitizadorDatosPersonales.limpiar(pregunta)).isEqualTo(pregunta);
    }

    @Test
    void aceptaNulo() {
        assertThat(SanitizadorDatosPersonales.limpiar(null)).isNull();
    }
}
