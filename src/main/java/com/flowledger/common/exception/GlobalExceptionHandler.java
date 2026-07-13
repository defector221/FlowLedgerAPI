package com.flowledger.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail notFound(
            ResourceNotFoundException ex,
            HttpServletRequest request
    ) {
        log.warn(
                "Resource not found. method={}, uri={}, message={}",
                request.getMethod(),
                request.getRequestURI(),
                ex.getMessage()
        );

        return problem(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(UnauthorizedException.class)
    ProblemDetail unauthorized(
            UnauthorizedException ex,
            HttpServletRequest request
    ) {
        log.warn(
                "Unauthorized request. method={}, uri={}, message={}",
                request.getMethod(),
                request.getRequestURI(),
                ex.getMessage()
        );

        return problem(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail conflict(
            ConflictException ex,
            HttpServletRequest request
    ) {
        log.warn(
                "Conflict. method={}, uri={}, message={}",
                request.getMethod(),
                request.getRequestURI(),
                ex.getMessage()
        );

        return problem(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler({
            BusinessException.class,
            MethodArgumentNotValidException.class
    })
    ProblemDetail badRequest(
            Exception ex,
            HttpServletRequest request
    ) {
        log.warn(
                "Bad request. method={}, uri={}, message={}",
                request.getMethod(),
                request.getRequestURI(),
                ex.getMessage()
        );

        return problem(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(
            Exception ex,
            HttpServletRequest request
    ) {
        log.error(
                "Unhandled exception. method={}, uri={}",
                request.getMethod(),
                request.getRequestURI(),
                ex
        );

        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred",
                request
        );
    }

    private ProblemDetail problem(
            HttpStatus status,
            String detail,
            HttpServletRequest request
    ) {
        ProblemDetail result = ProblemDetail.forStatusAndDetail(
                status,
                detail == null ? status.getReasonPhrase() : detail
        );

        result.setType(
                URI.create(
                        "https://flowledger.com/problems/" + status.value()
                )
        );

        result.setInstance(
                URI.create(request.getRequestURI())
        );

        return result;
    }
}