package com.oficinaagua.chatbot_api.registro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.oficinaagua.chatbot_api.api.model.Source;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;

class RegistroInteraccionServiceTest {

    private final DynamoDbClient dynamoDb = mock(DynamoDbClient.class);
    private final RegistroInteraccionService servicio =
            new RegistroInteraccionService(dynamoDb, "chatbot-interacciones");

    @Test
    void guardaPreguntaRespuestaFuentesYFecha() {
        Source fuente = new Source();
        fuente.setDocument("calculo-consumo.md");
        fuente.setSection("Cuota mínima y excedente");

        servicio.registrar("¿Cómo se calcula mi consumo?", "Se cobra la cuota mínima...", List.of(fuente));

        ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamoDb).putItem(captor.capture());
        PutItemRequest request = captor.getValue();
        Map<String, AttributeValue> item = request.item();

        assertThat(request.tableName()).isEqualTo("chatbot-interacciones");
        assertThat(item.get("id").s()).isNotBlank();
        assertThat(item.get("fecha").s()).isNotBlank();
        assertThat(item.get("pregunta").s()).isEqualTo("¿Cómo se calcula mi consumo?");
        assertThat(item.get("respuesta").s()).isEqualTo("Se cobra la cuota mínima...");
        Map<String, AttributeValue> primeraFuente = item.get("fuentes").l().get(0).m();
        assertThat(primeraFuente.get("documento").s()).isEqualTo("calculo-consumo.md");
        assertThat(primeraFuente.get("seccion").s()).isEqualTo("Cuota mínima y excedente");
    }

    @Test
    void noGuardaDatosPersonales() {
        servicio.registrar("Mi correo es ana@gmail.com y mi teléfono 5555-1234", "Gracias", List.of());

        ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamoDb).putItem(captor.capture());
        String pregunta = captor.getValue().item().get("pregunta").s();

        assertThat(pregunta).doesNotContain("ana@gmail.com").doesNotContain("5555-1234");
    }

    @Test
    void unFalloAlGuardarNoInterrumpeLaRespuesta() {
        when(dynamoDb.putItem(any(PutItemRequest.class)))
                .thenThrow(ResourceNotFoundException.builder().message("Tabla no existe").build());

        assertThatCode(() -> servicio.registrar("pregunta", "respuesta", List.of()))
                .doesNotThrowAnyException();
    }
}
