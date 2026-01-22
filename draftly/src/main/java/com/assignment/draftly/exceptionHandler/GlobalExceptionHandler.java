package com.assignment.draftly.exceptionHandler;

import io.jsonwebtoken.JwtException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleResourceNotFoundException(ResourceNotFoundException exception) {
        ApiError apiError = new ApiError(exception.getLocalizedMessage(), HttpStatus.NOT_FOUND);
        return new ResponseEntity<>(apiError, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthenticationException(AuthenticationException ex) {
        ApiError apiError = new ApiError(ex.getLocalizedMessage(), HttpStatus.UNAUTHORIZED);
        return new ResponseEntity<>(apiError, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(JwtException.class)
    public ResponseEntity<ApiError> handleJwtException(JwtException ex) {
        ApiError apiError =
                new ApiError(ex.getLocalizedMessage(), HttpStatus.UNAUTHORIZED);
        return new ResponseEntity<>(apiError, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleIllegalStateException(IllegalStateException ex) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        if (ex.getMessage() != null && ex.getMessage().contains("not connected Google account")) {
            status = HttpStatus.UNAUTHORIZED;
        }
        ApiError apiError = new ApiError(ex.getLocalizedMessage(), status);
        return new ResponseEntity<>(apiError, status);
    }

    @ExceptionHandler(GmailApiException.class)
    public ResponseEntity<ApiError> handleGmailApiException(GmailApiException ex) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        if (ex.getStatusCode() == 401) {
            status = HttpStatus.UNAUTHORIZED;
        } else if (ex.getStatusCode() == 403) {
            status = HttpStatus.FORBIDDEN;
        } else if (ex.getStatusCode() == 404) {
            status = HttpStatus.NOT_FOUND;
        } else if (ex.getStatusCode() >= 400 && ex.getStatusCode() < 500) {
            status = HttpStatus.BAD_REQUEST;
        }
        ApiError apiError = new ApiError(ex.getMessage(), status);
        return new ResponseEntity<>(apiError, status);
    }

    @ExceptionHandler(HttpClientErrorException.class)
    public ResponseEntity<ApiError> handleHttpClientErrorException(HttpClientErrorException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String errorMessage = "External API error: " + ex.getMessage();
        if (ex.getResponseBodyAsString() != null && !ex.getResponseBodyAsString().isEmpty()) {
            errorMessage = "External API error: " + ex.getResponseBodyAsString();
        }
        ApiError apiError = new ApiError(errorMessage, status);
        return new ResponseEntity<>(apiError, status);
    }

}
