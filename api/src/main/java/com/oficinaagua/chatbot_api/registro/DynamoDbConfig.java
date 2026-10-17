package com.oficinaagua.chatbot_api.registro;

import java.net.URI;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;

/**
 * HU-38: cliente de DynamoDB para el registro de prompt-response.
 * Con DYNAMODB_ENDPOINT definido usa DynamoDB Local (desarrollo con Docker Compose).
 * Con DYNAMODB_ENDPOINT vacío usa DynamoDB de AWS y las credenciales del task role de ECS.
 * Los tiempos de espera son cortos para que un DynamoDB lento o caído
 * no retrase la respuesta al usuario.
 */
@Configuration
public class DynamoDbConfig {

    @Bean
    public DynamoDbClient dynamoDbClient(
            @Value("${chatbot.dynamodb.endpoint:}") String endpoint,
            @Value("${chatbot.dynamodb.region}") String region,
            @Value("${chatbot.dynamodb.access-key}") String accessKey,
            @Value("${chatbot.dynamodb.secret-key}") String secretKey) {
        DynamoDbClientBuilder builder = DynamoDbClient.builder()
                .region(Region.of(region))
                .overrideConfiguration(config -> config
                        .apiCallTimeout(Duration.ofSeconds(2))
                        .apiCallAttemptTimeout(Duration.ofSeconds(1)));

        if (endpoint == null || endpoint.isBlank()) {
            // AWS: el SDK toma las credenciales temporales del task role de ECS.
            builder.credentialsProvider(DefaultCredentialsProvider.builder().build());
        } else {
            builder.endpointOverride(URI.create(endpoint))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKey, secretKey)));
        }
        return builder.build();
    }
}
