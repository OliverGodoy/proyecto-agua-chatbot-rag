package com.oficinaagua.chatbot_api.registro;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.oficinaagua.chatbot_api.api.model.Source;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

/**
 * HU-38: guarda cada pregunta y respuesta del chatbot en DynamoDB.
 * Un fallo al guardar nunca se propaga: solo se registra en el log,
 * para que el usuario reciba su respuesta de todas formas.
 */
@Service
public class RegistroInteraccionService {

    private static final Logger log = LoggerFactory.getLogger(RegistroInteraccionService.class);

    private final DynamoDbClient dynamoDb;
    private final String tabla;

    public RegistroInteraccionService(DynamoDbClient dynamoDb,
                                      @Value("${chatbot.dynamodb.tabla}") String tabla) {
        this.dynamoDb = dynamoDb;
        this.tabla = tabla;
    }

    public void registrar(String pregunta, String respuesta, List<Source> fuentes) {
        try {
            Map<String, AttributeValue> item = new HashMap<>();
            item.put("id", texto(UUID.randomUUID().toString()));
            item.put("fecha", texto(Instant.now().toString()));
            item.put("pregunta", texto(SanitizadorDatosPersonales.limpiar(pregunta)));
            item.put("respuesta", texto(SanitizadorDatosPersonales.limpiar(respuesta)));
            item.put("fuentes", AttributeValue.fromL(convertirFuentes(fuentes)));

            dynamoDb.putItem(PutItemRequest.builder()
                    .tableName(tabla)
                    .item(item)
                    .build());
        } catch (RuntimeException e) {
            log.warn("No se pudo registrar la interacción en DynamoDB: {}", e.getMessage());
        }
    }

    private static List<AttributeValue> convertirFuentes(List<Source> fuentes) {
        if (fuentes == null) {
            return List.of();
        }
        return fuentes.stream()
                .map(fuente -> {
                    Map<String, AttributeValue> mapa = new HashMap<>();
                    mapa.put("documento", texto(fuente.getDocument()));
                    if (fuente.getSection() != null) {
                        mapa.put("seccion", texto(fuente.getSection()));
                    }
                    return AttributeValue.fromM(mapa);
                })
                .toList();
    }

    private static AttributeValue texto(String valor) {
        return AttributeValue.fromS(valor == null ? "" : valor);
    }
}
