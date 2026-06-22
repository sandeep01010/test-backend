package com.examplatform.exam.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

/**
 * Internal HTTP client for payment-service — checks whether a student holds an active
 * access grant for a category or group. Calls payment-service directly (bypassing the
 * gateway) with a trusted X-User-Id/X-User-Role header pair, same pattern payment-service's
 * own UserServiceClient uses against user-service.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentAccessClient {

    private final RestTemplate restTemplate;

    @Value("${services.payment-service.url:http://payment-service:8085}")
    private String paymentServiceUrl;

    /** Fails OPEN (returns true / "has access") on any connectivity error rather than
     *  blocking every attempt platform-wide if payment-service happens to be down — a
     *  locked exam being briefly attemptable during an outage is a far smaller problem
     *  than every exam (locked or not) becoming unattemptable. */
    public boolean hasAccess(UUID studentId, String scopeType, String scopeCode) {
        String url = paymentServiceUrl + "/api/v1/payments/access?scopeType=" + scopeType + "&scopeCode=" + scopeCode;

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", studentId.toString());
        headers.set("X-User-Role", "STUDENT");

        try {
            var response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            Object hasAccess = response.getBody() != null ? response.getBody().get("hasAccess") : null;
            return Boolean.TRUE.equals(hasAccess);
        } catch (RestClientException e) {
            log.error("Could not reach payment-service to check access for student={} scope={}:{} — failing open: {}",
                    studentId, scopeType, scopeCode, e.getMessage());
            return true;
        }
    }
}
