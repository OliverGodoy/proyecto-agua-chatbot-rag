package com.oficinaagua.chatbot_api.registro;

import java.util.regex.Pattern;

/**
 * HU-38: oculta datos personales antes de guardar una interacción.
 * Cubre correos, números largos (teléfono, DPI, tarjeta, número de contador)
 * y NIT. Los nombres de personas no se pueden detectar de forma confiable.
 */
public final class SanitizadorDatosPersonales {

    private static final Pattern CORREO =
            Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");
    private static final Pattern NIT =
            Pattern.compile("\\b\\d{5,8}-[\\dkK]\\b");
    private static final Pattern NUMERO_LARGO =
            Pattern.compile("\\d(?:[\\s-]?\\d){7,}");

    private SanitizadorDatosPersonales() {
    }

    public static String limpiar(String texto) {
        if (texto == null) {
            return null;
        }
        String limpio = CORREO.matcher(texto).replaceAll("[CORREO]");
        limpio = NIT.matcher(limpio).replaceAll("[NIT]");
        limpio = NUMERO_LARGO.matcher(limpio).replaceAll("[NUMERO]");
        return limpio;
    }
}
