package com.flowledger.common.exception;

import com.flowledger.search.exception.SearchUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SearchUnavailableException.class)
    ProblemDetail searchUnavailable(SearchUnavailableException ex, HttpServletRequest request) {
        log.error("Search unavailable. method={}, uri={}", request.getMethod(), request.getRequestURI(), ex);
        ProblemDetail result = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                ex.getMessage() == null || ex.getMessage().isBlank()
                        ? "Search is temporarily unavailable. Please try again."
                        : ex.getMessage());
        result.setTitle("Search Unavailable");
        result.setType(URI.create("https://flowledger.com/problems/503"));
        result.setInstance(URI.create(request.getRequestURI()));
        return result;
    }

    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail responseStatus(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatusCode statusCode = ex.getStatusCode();
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String detail = ex.getReason();
        if (detail == null || detail.isBlank()) {
            detail = status.getReasonPhrase();
        }
        if (status.is5xxServerError()) {
            log.error(
                    "ResponseStatusException. method={}, uri={}, status={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    status.value(),
                    ex);
        }
        return problem(status, detail, request, null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, ex.getMessage(), request, null);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail notFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage(), request, null);
    }

    @ExceptionHandler(UnauthorizedException.class)
    ProblemDetail unauthorized(UnauthorizedException ex, HttpServletRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, ex.getMessage(), request, null);
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail conflict(ConflictException ex, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, ex.getMessage(), request, null);
    }

    @ExceptionHandler(BusinessException.class)
    ProblemDetail business(BusinessException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, ex.getMessage(), request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }
        return problem(HttpStatus.BAD_REQUEST, "Validation failed", request, fieldErrors);
    }

    @ExceptionHandler(ValidationException.class)
    ProblemDetail validationException(ValidationException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, ex.getMessage(), request, ex.getFieldErrors());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail dataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation. method={}, uri={}", request.getMethod(), request.getRequestURI());
        return problem(HttpStatus.BAD_REQUEST, "Invalid data submitted", request, null);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception. method={}, uri={}", request.getMethod(), request.getRequestURI(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request, null);
    }

    private ProblemDetail problem(
            HttpStatus status, String detail, HttpServletRequest request, Map<String, String> fieldErrors) {
        ProblemDetail result =
                ProblemDetail.forStatusAndDetail(status, detail == null ? status.getReasonPhrase() : detail);
        result.setTitle(status.getReasonPhrase());
        result.setType(URI.create("https://flowledger.com/problems/" + status.value()));
        result.setInstance(URI.create(request.getRequestURI()));
        if (fieldErrors != null && !fieldErrors.isEmpty()) {
            result.setProperty("fieldErrors", fieldErrors);
        }
        return result;
    }
}
