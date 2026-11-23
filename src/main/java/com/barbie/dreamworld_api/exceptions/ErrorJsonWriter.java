package com.barbie.dreamworld_api.exceptions;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Escreve o mesmo envelope ErrorResponse usado pelo @RestControllerAdvice. */
public final class ErrorJsonWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ErrorJsonWriter() {}

    public static void write(HttpServletResponse response,
                             HttpServletRequest request,
                             HttpStatus status,
                             String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json;charset=UTF-8");

        // String ISO-8601 evita depender de módulos Jackson de java.time neste
        // escritor de baixo nível (usado por filtros, antes do MVC).
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("path", request.getRequestURI());

        MAPPER.writeValue(response.getWriter(), body);
    }
}
