package com.flowledger.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail notFound(ResourceNotFoundException ex, HttpServletRequest request) { return problem(HttpStatus.NOT_FOUND, ex.getMessage(), request); }
    @ExceptionHandler({UnauthorizedException.class})
    ProblemDetail unauthorized(UnauthorizedException ex, HttpServletRequest request) { return problem(HttpStatus.UNAUTHORIZED, ex.getMessage(), request); }
    @ExceptionHandler({ConflictException.class})
    ProblemDetail conflict(ConflictException ex, HttpServletRequest request) { return problem(HttpStatus.CONFLICT, ex.getMessage(), request); }
    @ExceptionHandler({BusinessException.class, MethodArgumentNotValidException.class})
    ProblemDetail badRequest(Exception ex, HttpServletRequest request) { return problem(HttpStatus.BAD_REQUEST, ex.getMessage(), request); }
    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception ex, HttpServletRequest request) { return problem(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request); }
    private ProblemDetail problem(HttpStatus status, String detail, HttpServletRequest request) {
        ProblemDetail result = ProblemDetail.forStatusAndDetail(status, detail == null ? status.getReasonPhrase() : detail);
        result.setType(URI.create("https://flowledger.com/problems/" + status.value()));
        result.setInstance(URI.create(request.getRequestURI()));
        return result;
    }
}
