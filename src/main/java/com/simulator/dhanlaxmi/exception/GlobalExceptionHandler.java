package com.simulator.dhanlaxmi.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidMercodeException.class)
    public ResponseEntity<Map<String, String>> handleInvalidMercode(InvalidMercodeException e) {
        return body(HttpStatus.BAD_REQUEST, "INVALID_MERCODE", e.getMessage());
    }

    @ExceptionHandler(InvalidChecksumException.class)
    public ResponseEntity<Map<String, String>> handleInvalidChecksum(InvalidChecksumException e) {
        return body(HttpStatus.FORBIDDEN, "INVALID_CHECKSUM", e.getMessage());
    }

    @ExceptionHandler(DuplicateTransactionException.class)
    public ResponseEntity<Map<String, String>> handleDuplicate(DuplicateTransactionException e) {
        return body(HttpStatus.CONFLICT, "DUPLICATE_TRANSACTION", e.getMessage());
    }

    @ExceptionHandler(TransactionNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(TransactionNotFoundException e) {
        return body(HttpStatus.NOT_FOUND, "TRANSACTION_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(TransactionAlreadyResolvedException.class)
    public ResponseEntity<Map<String, String>> handleAlreadyResolved(TransactionAlreadyResolvedException e) {
        return body(HttpStatus.CONFLICT, "ALREADY_RESOLVED", e.getMessage());
    }

    private ResponseEntity<Map<String, String>> body(HttpStatus status, String error, String message) {
        Map<String, String> response = new LinkedHashMap<>();
        response.put("error", error);
        response.put("message", message);
        return ResponseEntity.status(status).body(response);
    }
}
