package io.github.atomoty.faultpilot.server.api;

import io.github.atomoty.faultpilot.server.config.ProjectRegistry;
import io.github.atomoty.faultpilot.server.security.IngestionAuthService;
import io.github.atomoty.faultpilot.server.service.DiagnosisService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Maps domain exceptions to HTTP responses.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ProjectRegistry.UnknownProjectException.class)
    public ResponseEntity<Map<String, String>> unknownProject(ProjectRegistry.UnknownProjectException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(DiagnosisService.InvalidRequestException.class)
    public ResponseEntity<Map<String, String>> invalidRequest(DiagnosisService.InvalidRequestException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(IngestionAuthService.MissingTokenException.class)
    public ResponseEntity<Map<String, String>> missingToken(IngestionAuthService.MissingTokenException ex) {
        return error(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(IngestionAuthService.ForbiddenTokenException.class)
    public ResponseEntity<Map<String, String>> forbiddenToken(IngestionAuthService.ForbiddenTokenException ex) {
        return error(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> validation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(e -> e.getField() + " " + e.getDefaultMessage())
                .orElse("请求参数非法");
        return error(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
