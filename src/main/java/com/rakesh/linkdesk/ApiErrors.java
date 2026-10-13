package com.rakesh.linkdesk;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiErrors {
  private static final Logger LOG = LoggerFactory.getLogger(ApiErrors.class);

  @ExceptionHandler(LinkFailure.class)
  ResponseEntity<Map<String, Object>> failure(LinkFailure ex) {
    return error(ex.status(), ex.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException ex) {
    String message =
        ex.getBindingResult().getFieldErrors().stream()
            .map(field -> field.getField() + ": " + field.getDefaultMessage())
            .sorted()
            .findFirst()
            .orElse("Invalid request.");
    return error(400, message);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<Map<String, Object>> unreadable() {
    return error(400, "Send a valid JSON request with correctly typed fields.");
  }

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<Map<String, Object>> unavailable(DataAccessException ex) {
    LOG.warn("Database operation unavailable; exceptionType={}", ex.getClass().getSimpleName());
    return error(503, "Storage is temporarily unavailable. Please retry.");
  }

  private ResponseEntity<Map<String, Object>> error(int status, String message) {
    return ResponseEntity.status(status).body(Map.of("status", status, "message", message));
  }
}
