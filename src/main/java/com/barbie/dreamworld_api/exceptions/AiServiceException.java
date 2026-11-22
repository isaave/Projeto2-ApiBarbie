package com.barbie.dreamworld_api.exceptions;

import org.springframework.http.HttpStatus;

/**
 * Erros originados na integração com o provedor de IA generativa (Gemini).
 * Carrega o HttpStatus a ser retornado ao cliente, permitindo diferenciar:
 *   - configuração ausente / não autorizado  → 503 Service Unavailable
 *   - timeout / indisponibilidade transitória → 503 Service Unavailable
 *   - rate limit do provedor                  → 429 Too Many Requests
 *   - resposta da IA rejeitada ou ilegível     → 502 Bad Gateway
 */
public class AiServiceException extends RuntimeException {

    private final HttpStatus status;

    public AiServiceException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public AiServiceException(String message, HttpStatus status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
