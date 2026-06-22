package com.examplatform.payment.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Internal HTTP client for exam-service communication — fetches the authoritative price
 * for a category or group so the client can never spoof the amount being charged.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExamServiceClient {

    private final RestTemplate restTemplate;

    @Value("${services.exam-service.url:http://exam-service:8082}")
    private String examServiceUrl;

    /** Returns the price in paise for a category or group code, or null if not found. */
    @SuppressWarnings("unchecked")
    public Long fetchPriceInPaise(String scopeType, String scopeCode) {
        String path = "CATEGORY".equalsIgnoreCase(scopeType)
                ? "/api/v1/exams/categories/" + scopeCode
                : "/api/v1/exams/category-groups/" + scopeCode;
        String url = examServiceUrl + path;
        try {
            Map<String, Object> body = restTemplate.getForObject(url, Map.class);
            if (body == null) return null;
            Object price = body.get("priceInPaise");
            return price instanceof Number ? ((Number) price).longValue() : null;
        } catch (RestClientException e) {
            log.error("Failed to fetch price for {} {}: {}", scopeType, scopeCode, e.getMessage());
            throw new RuntimeException("Could not look up price for " + scopeCode, e);
        }
    }
}
