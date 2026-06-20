package com.examplatform.gateway.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @RequestMapping("/exam")
    public ResponseEntity<Map<String, Object>> examFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "error", "SERVICE_UNAVAILABLE",
                "service", "exam-service",
                "message", "Exam service is temporarily unavailable. Please try again in a moment.",
                "timestamp", Instant.now().toString()
        ));
    }

    @RequestMapping("/session")
    public ResponseEntity<Map<String, Object>> sessionFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "error", "SERVICE_UNAVAILABLE",
                "service", "test-engine",
                "message", "Test session service is temporarily unavailable. Your answers are safe. Please reconnect.",
                "timestamp", Instant.now().toString()
        ));
    }
}
