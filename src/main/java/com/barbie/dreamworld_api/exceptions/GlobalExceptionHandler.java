package com.barbie.dreamworld_api.exceptions;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Intercepta todas as exceções e garante respostas JSON no formato ErrorResponse.
 *
 * Mapeamento:
 *   MethodArgumentNotValidException   → 400 Bad Request
 *   HttpMessageNotReadableException   → 400 Bad Request
 *   ResourceNotFoundException         → 404 Not Found
 *   ConflictException                 → 409 Conflict
 *   BusinessException                 → 422 Unprocessable Entity
 *   Exception (genérico)              → 500 Internal Server Error
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        List<ErrorResponse.FieldErrorDetail> fieldErrors = ex.getBindingResult()
                .getAllErrors().stream()
                .map(error -> {
                    String campo = error instanceof FieldError fe ? fe.getField() : error.getObjectName();
                    Object valor = error instanceof FieldError fe ? fe.getRejectedValue() : null;
                    if (campo != null && éCampoSensível(campo)) {
                        valor = null;
                    }
                    return ErrorResponse.FieldErrorDetail.builder()
                            .field(campo)
                            .rejectedValue(valor)
                            .message(error.getDefaultMessage())
                            .build();
                }).toList();

        return ResponseEntity.badRequest().body(
                ErrorResponse.builder()
                        .timestamp(Instant.now())
                        .status(400)
                        .error("Bad Request")
                        .message("Erro de validação nos campos da requisição.")
                        .path(request.getRequestURI())
                        .fieldErrors(fieldErrors)
                        .build());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleInvalidFormat(
            HttpMessageNotReadableException ex, HttpServletRequest request) {

        String message = "Corpo da requisição inválido.";

        Throwable cause = ex.getMostSpecificCause();
        if (cause != null) {
            String causeMessage = cause.getMessage();

            if (causeMessage != null && causeMessage.contains("LocalDate")) {
                message = "Data inválida ou formato incorreto. Use uma data existente no formato YYYY-MM-DD.";
            }
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(build(400, "Bad Request", message, request));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(build(404, "Not Found", ex.getMessage(), request));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(
            ConflictException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(build(409, "Conflict", ex.getMessage(), request));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(
            BusinessException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(build(422, "Unprocessable Entity", ex.getMessage(), request));
    }

    @ExceptionHandler(AiServiceException.class)
    public ResponseEntity<ErrorResponse> handleAiService(
            AiServiceException ex, HttpServletRequest request) {
        log.error("Falha na integração com o serviço de IA em {}: {}",
                request.getRequestURI(), ex.getMessage(), ex.getCause());
        return ResponseEntity.status(ex.getStatus())
                .body(build(ex.getStatus().value(), ex.getStatus().getReasonPhrase(),
                        ex.getMessage(), request));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(build(400, "Bad Request",
                "Valor inválido para o parâmetro '" + ex.getName() + "'.", request));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(build(400, "Bad Request",
                "O parâmetro '" + ex.getParameterName() + "' é obrigatório.", request));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraint(
            ConstraintViolationException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(build(400, "Bad Request",
                "Dados inválidos: " + ex.getMessage(), request));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(build(405, "Method Not Allowed",
                        "Método " + ex.getMethod() + " não suportado nesta rota.", request));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(
            NoResourceFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(build(404, "Not Found", "Recurso não encontrado.", request));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(
            Exception ex, HttpServletRequest request) {
        log.error("Erro inesperado em {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(build(500, "Internal Server Error",
                        "Ocorreu um erro inesperado. Tente novamente mais tarde.", request));
    }

    private boolean éCampoSensível(String campo) {
        String c = campo.toLowerCase(Locale.ROOT);
        return c.contains("senha") || c.contains("password") || c.contains("secret")
                || c.contains("token") || c.contains("apikey") || c.contains("api-key")
                || c.contains("chave");
    }

    private ErrorResponse build(int status, String error, String message,
                                HttpServletRequest request) {
        return ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(status)
                .error(error)
                .message(message)
                .path(request.getRequestURI())
                .build();
    }
}
