package com.oficinaagua.chatbot_api.ingesta;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * HU-33: ingesta bajo demanda. Solo se ejecuta si la aplicación arranca con --ingesta,
 * y al terminar cierra la aplicación.
 */
@Component
public class IngestaRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IngestaRunner.class);

    private final IngestaService servicio;
    private final ConfigurableApplicationContext contexto;

    public IngestaRunner(IngestaService servicio, ConfigurableApplicationContext contexto) {
        this.servicio = servicio;
        this.contexto = contexto;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption("ingesta")) {
            return;
        }
        int codigo = 0;
        try {
            servicio.ejecutar();
        } catch (Exception e) {
            log.error("La ingesta falló: {}", e.getMessage());
            codigo = 1;
        }
        int salida = codigo;
        System.exit(SpringApplication.exit(contexto, () -> salida));
    }
}
