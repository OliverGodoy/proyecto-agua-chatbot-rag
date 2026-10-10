package com.oficinaagua.chatbot_api.registro;

import java.net.URI;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * HU-38: cliente de DynamoDB para el registro de prompt-response.
 * Los tiempos de espera son cortos para que un DynamoDB lento o caído
 * no retrase la respuesta al usuario.
 */
@Configuration
public class DynamoDbConfig {

    @Bean
    public DynamoDbClient dynamoDbClient(
            @Value("${chatbot.dynamodb.endpoint}") String endpoint,
            @Value("${chatbot.dynamodb.region}") String region,
            @Value("${chatbot.dynamodb.access-key}") String accessKey,
            @Value("${chatbot.dynamodb.secret-key}") String secretKey) {
        return DynamoDbClient.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .overrideConfiguration(config -> config
                        .apiCallTimeout(Duration.ofSeconds(2))
                        .apiCallAttemptTimeout(Duration.ofSeconds(1)))
                .build();
    }
}
