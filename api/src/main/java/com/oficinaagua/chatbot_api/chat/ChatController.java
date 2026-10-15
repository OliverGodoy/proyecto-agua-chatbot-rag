package com.oficinaagua.chatbot_api.chat;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.oficinaagua.chatbot_api.api.ChatApi;
import com.oficinaagua.chatbot_api.api.model.ChatRequest;
import com.oficinaagua.chatbot_api.api.model.ChatResponse;

/**
 * HU-34: implementa la interfaz generada del contrato OpenAPI (contract-first).
 * La ruta, el método HTTP y la validación vienen de ChatApi.
 */
@RestController
public class ChatController implements ChatApi {

    private final ChatService servicio;

    public ChatController(ChatService servicio) {
        this.servicio = servicio;
    }

    @Override
    public ResponseEntity<ChatResponse> askQuestion(ChatRequest chatRequest) {
        return ResponseEntity.ok(servicio.responder(chatRequest.getQuestion()));
    }
}
