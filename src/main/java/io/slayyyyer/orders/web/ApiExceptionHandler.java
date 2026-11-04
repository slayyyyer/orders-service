package io.slayyyyer.orders.web;

import io.slayyyyer.orders.service.ConflictException;
import io.slayyyyer.orders.service.NotFoundException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * RFC 9457 problem details for every failure path. The shape is stable and documented in
 * the README: type, title, status, detail, plus a field map for validation failures.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final String BASE = "https://slayyyyer.github.io/orders-service/problems/";

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail notFound(NotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setType(URI.create(BASE + "not-found"));
        detail.setTitle("Resource not found");
        detail.setProperty("resource", ex.resource());
        detail.setProperty("id", ex.id());
        return detail;
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail conflict(ConflictException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        detail.setType(URI.create(BASE + "conflict"));
        detail.setTitle("Request conflicts with the current state");
        return detail;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail badRequest(IllegalArgumentException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        detail.setType(URI.create(BASE + "invalid-request"));
        detail.setTitle("Invalid request");
        return detail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        ProblemDetail detail =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "one or more fields are invalid");
        detail.setType(URI.create(BASE + "validation-failed"));
        detail.setTitle("Validation failed");
        detail.setProperty("errors", fields);
        return detail;
    }
}
