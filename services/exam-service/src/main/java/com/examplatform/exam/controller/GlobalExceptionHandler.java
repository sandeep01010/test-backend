package com.examplatform.exam.controller;

import com.examplatform.exam.exception.PaymentRequiredException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * exam-service had no centralized exception mapping before — unhandled RuntimeExceptions
 * just fell through to Spring's generic 500. Adding PaymentRequiredException -> 402 here
 * (the new lock-gating signal the frontend needs), plus IllegalStateException -> 409 since
 * that's what callers (e.g. the frontend's ensureAttempt 409 check for "exam closed") have
 * always assumed was already happening.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PaymentRequiredException.class)
    public ResponseEntity<Map<String, Object>> handlePaymentRequired(PaymentRequiredException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "PAYMENT_REQUIRED");
        body.put("message", ex.getMessage());
        body.put("scopeType", ex.getScopeType());
        body.put("scopeCode", ex.getScopeCode());
        body.put("priceInPaise", ex.getPriceInPaise());
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(body);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "CONFLICT", "message", ex.getMessage()));
    }
}
