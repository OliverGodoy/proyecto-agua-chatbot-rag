package com.oficinaagua.chatbot_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
@SpringBootApplication
public class ChatbotApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(ChatbotApiApplication.class, args);
	}

}
