package com.oficinaagua.chatbot_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;

@ConfigurationPropertiesScan
@SpringBootApplication
@EnableFeignClients(basePackages = "com.oficinaagua.chatbot_api.llm.cliente")
public class ChatbotApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(ChatbotApiApplication.class, args);
	}

}
