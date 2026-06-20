package com.examplatform.payment.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

/**
 * Internal HTTP client for user-service communication.
 * Calls user-service directly (bypassing API gateway) with X-User-Id header.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserServiceClient {

    private final RestTemplate restTemplate;

    @Value("${services.user-service.url:http://user-service:8081}")
    private String userServiceUrl;

    /**
     * Activates a subscription for a user by calling user-service's internal subscribe endpoint.
     * This is an internal service-to-service call — no JWT required, uses X-User-Id header.
     *
     * @param userId      the user's UUID
     * @param plan        the subscription plan (e.g., "PRO_ANNUAL")
     * @param durationDays number of days the subscription lasts
     * @throws RuntimeException if user-service returns an error
     */
    public void activateSubscription(UUID userId, String plan, int durationDays) {
        String url = userServiceUrl + "/api/v1/auth/subscribe";
        log.info("Activating subscription for user: {} plan: {} duration: {} days", userId, plan, durationDays);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Id", userId.toString());
        headers.set("X-Internal-Service", "payment-service");

        Map<String, Object> body = Map.of(
                "plan", plan,
                "durationDays", String.valueOf(durationDays)
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Subscription activated successfully for user: {} plan: {}", userId, plan);
            } else {
                log.warn("Unexpected response from user-service: status={}", response.getStatusCode());
                throw new RuntimeException("Failed to activate subscription: unexpected status " + response.getStatusCode());
            }
        } catch (HttpClientErrorException e) {
            log.error("Client error activating subscription for user: {} - status: {} body: {}",
                    userId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Failed to activate subscription: " + e.getMessage(), e);
        } catch (HttpServerErrorException e) {
            log.error("Server error from user-service activating subscription for user: {} - status: {} body: {}",
                    userId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("User service error while activating subscription", e);
        } catch (Exception e) {
            log.error("Unexpected error calling user-service for user: {}", userId, e);
            throw new RuntimeException("Failed to reach user-service to activate subscription", e);
        }
    }
}
