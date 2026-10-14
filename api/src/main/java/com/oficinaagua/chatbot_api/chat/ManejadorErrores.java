package com.oficinaagua.chatbot_api.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import com.oficinaagua.chatbot_api.api.model.ErrorResponse;
import com.oficinaagua.chatbot_api.api.model.ErrorResponse.CodeEnum;
import com.oficinaagua.chatbot_api.embedding.EmbeddingException;
import com.oficinaagua.chatbot_api.llm.LlmNoDisponibleException;
import com.oficinaagua.chatbot_api.llm.LlmTimeoutException;

/**
 * Convierte las excepciones en el ErrorResponse del contrato OpenAPI,
 * con mensajes amigables y sin detalles internos para el cliente.
 */
@RestControllerAdvice
public class ManejadorErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

    @ExceptionHandler({MethodArgumentNotValidException.class, HandlerMethodValidationException.class,
            HttpMessageNotReadableException.class, PreguntaInvalidaException.class})
    public ResponseEntity<ErrorResponse> preguntaInvalida(Exception e) {
        return error(HttpStatus.BAD_REQUEST, CodeEnum.INVALID_QUESTION,
                "La pregunta debe tener entre 1 y 500 caracteres.");
    }

    @ExceptionHandler({LlmNoDisponibleException.class, EmbeddingException.class})
    public ResponseEntity<ErrorResponse> servicioNoDisponible(RuntimeException e) {
        log.warn("Proveedor de IA no disponible: {}", e.getMessage());
        return error(HttpStatus.SERVICE_UNAVAILABLE, CodeEnum.LLM_UNAVAILABLE,
                "El asistente no está disponible en este momento. Intenta de nuevo en unos minutos.");
    }

    @ExceptionHandler(LlmTimeoutException.class)
    public ResponseEntity<ErrorResponse> tiempoAgotado(LlmTimeoutException e) {
        log.warn("El LLM no respondió a tiempo: {}", e.getMessage());
        return error(HttpStatus.GATEWAY_TIMEOUT, CodeEnum.LLM_TIMEOUT,
                "El asistente tardó demasiado en responder. Intenta de nuevo.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> errorInesperado(Exception e) throws Exception {
        if (e instanceof org.springframework.web.ErrorResponse) {
            throw e; // 404, 405, etc.: los responde Spring con su código correcto
        }
        log.error("Error inesperado al responder la pregunta", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, CodeEnum.INTERNAL_ERROR,
                "Ocurrió un error inesperado. Intenta de nuevo más tarde.");
    }

    private static ResponseEntity<ErrorResponse> error(HttpStatus estado, CodeEnum codigo, String mensaje) {
        return ResponseEntity.status(estado).body(new ErrorResponse().code(codigo).message(mensaje));
    }
}
