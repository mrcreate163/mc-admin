package com.socialnetwork.adminbot.exception;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.errors.ResourceNotFoundException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Централизованный обработчик исключений для REST API.
 * Перехватывает исключения из всех контроллеров и возвращает унифицированные ответы.
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(
            IllegalArgumentException ex,
            WebRequest request) {
        log.warn("Validation Error at {}: {}",
                request.getDescription(false),
                ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(buildErrorResponse(
                        false,
                        ex.getMessage(),
                        HttpStatus.BAD_REQUEST.value()
                ));

    }

    /**
     * Обработка ситуаций "ресурс не найден"
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleResourceNotFound(
            ResourceNotFoundException ex,
            WebRequest request
    ) {
        log.warn("Resource not found at {}: {}",
                request.getDescription(false),
                ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(buildErrorResponse(
                        false,
                        ex.getMessage(),
                        HttpStatus.NOT_FOUND.value()
                ));
    }

    /**
     * Обработка всех остальных непредвиденных ошибок
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(
            Exception ex,
            WebRequest request
    ) {
        log.warn("Unexpected error at {}: {}",
                request.getDescription(false),
                ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildErrorResponse(
                        false,
                        ex.getMessage(),
                        HttpStatus.INTERNAL_SERVER_ERROR.value()
                ));
    }

    /**
     * Обработка превышения Rate Limit.
     * Возвращает 429 Too Many Requests с заголовком Retry-After.
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimitExceeded(
            RateLimitExceededException e,
            WebRequest request
    ) {
        log.warn("Rate limit exceeded at {}: {}",
                request.getDescription(false),
                e.getMessage());

        HttpHeaders headers = new HttpHeaders();
        headers.add("Retry-After", String.valueOf(e.getRetryAfterSeconds()));
        headers.add("X-Rate-Limit-Retry-After-Seconds", String.valueOf(e.getRetryAfterSeconds()));

        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .headers(headers)
                .body(buildErrorResponse(
                        false,
                        e.getMessage(),
                        HttpStatus.TOO_MANY_REQUESTS.value()
                ));
    }


    /**
     * Вспомогательный метод для построения единообразного формата ошибок
     */
    private Map<String, Object> buildErrorResponse(boolean success, String error, int statusCode) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("error", error);
        response.put("timestamp", LocalDateTime.now());
        response.put("status", statusCode);

        return response;
    }
}
