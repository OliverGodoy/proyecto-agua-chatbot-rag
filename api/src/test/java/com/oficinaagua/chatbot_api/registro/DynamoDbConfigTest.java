package com.oficinaagua.chatbot_api.registro;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.junit.jupiter.api.Test;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

class DynamoDbConfigTest {

    private final DynamoDbConfig config = new DynamoDbConfig();

    @Test
    void conEndpointUsaDynamoDbLocal() {
        try (DynamoDbClient cliente = config.dynamoDbClient("http://localhost:8000", "us-east-1", "local", "local")) {
            assertThat(cliente.serviceClientConfiguration().endpointOverride())
                    .contains(URI.create("http://localhost:8000"));
        }
    }

    @Test
    void sinEndpointUsaDynamoDbDeAws() {
        try (DynamoDbClient cliente = config.dynamoDbClient("", "us-east-1", "local", "local")) {
            assertThat(cliente.serviceClientConfiguration().endpointOverride()).isEmpty();
        }
    }
}
