package com.examplatform.payment.service;

import com.examplatform.payment.client.ExamServiceClient;
import com.examplatform.payment.client.UserServiceClient;
import com.examplatform.payment.dto.*;
import com.examplatform.payment.gateway.PaymentGatewayService;
import com.examplatform.payment.model.AccessGrant;
import com.examplatform.payment.model.Payment;
import com.examplatform.payment.repository.AccessGrantRepository;
import com.examplatform.payment.repository.PaymentRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final String KAFKA_TOPIC_PAYMENT_EVENTS = "payment-events";
    private static final String TEST_SUCCESS_SIGNATURE = "TEST_SUCCESS";

    // Plan registry: plan name → [amountInPaise, durationDays]
    private static final Map<String, long[]> PLAN_REGISTRY = Map.of(
            "PRO_ANNUAL",    new long[]{24900L, 365},
            "PRO_MONTHLY",   new long[]{2999L,  30},
            "BASIC_ANNUAL",  new long[]{9900L,  365},
            "BASIC_MONTHLY", new long[]{1199L,  30}
    );

    private static final int SCOPED_ACCESS_DURATION_DAYS = 365; // 1 year, per product decision

    private final PaymentRepository paymentRepository;
    private final AccessGrantRepository accessGrantRepository;
    private final PaymentGatewayService gatewayService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final UserServiceClient userServiceClient;
    private final ExamServiceClient examServiceClient;

    @Value("${payment.gateway.dev-mode:true}")
    private boolean devMode;

    // ─────────────────────────────────────────────────────────────────────────
    // Create Order
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public CreateOrderResponse createOrder(UUID userId, CreateOrderRequest req) {
        // Validate plan
        long[] planDetails = PLAN_REGISTRY.get(req.getPlan().toUpperCase());
        if (planDetails == null) {
            throw new IllegalArgumentException("Invalid plan: " + req.getPlan()
                    + ". Valid plans: " + String.join(", ", PLAN_REGISTRY.keySet()));
        }

        BigDecimal amount = BigDecimal.valueOf(planDetails[0]);
        int durationDays  = (int) planDetails[1];

        // Generate unique internal order ID
        String orderId = generateOrderId();

        // Create order in payment gateway
        String gatewayOrderId = gatewayService.createGatewayOrder(orderId, amount, "INR");

        // Set order expiry: 15 minutes from now
        Instant expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES);

        // Persist payment record
        Payment payment = Payment.builder()
                .userId(userId)
                .orderId(orderId)
                .gatewayOrderId(gatewayOrderId)
                .amount(amount)
                .currency("INR")
                .plan(req.getPlan().toUpperCase())
                .durationDays(durationDays)
                .status(Payment.PaymentStatus.CREATED)
                .paymentMethod(req.getPaymentMethod())
                .upiId(req.getUpiId())
                .expiresAt(expiresAt)
                .build();

        paymentRepository.save(payment);
        log.info("Payment order created: orderId={} userId={} plan={} amount={}", orderId, userId, req.getPlan(), amount);

        // Publish Kafka event
        publishEvent(KAFKA_TOPIC_PAYMENT_EVENTS, Map.of(
                "event",       "PAYMENT_ORDER_CREATED",
                "orderId",     orderId,
                "userId",      userId.toString(),
                "plan",        req.getPlan().toUpperCase(),
                "amount",      amount,
                "gatewayOrderId", gatewayOrderId,
                "timestamp",   Instant.now().toString()
        ));

        return CreateOrderResponse.builder()
                .orderId(orderId)
                .gatewayOrderId(gatewayOrderId)
                .amount(amount)
                .currency("INR")
                .plan(req.getPlan().toUpperCase())
                .durationDays(durationDays)
                .expiresAt(expiresAt)
                .keyId(gatewayService.getKeyId())
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Create Scoped Order (buy a category or category-group — 1 year access)
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public CreateScopedOrderResponse createScopedOrder(UUID userId, CreateScopedOrderRequest req) {
        String scopeType = req.getScopeType().toUpperCase();
        String scopeCode  = req.getScopeCode();

        Long priceInPaise = examServiceClient.fetchPriceInPaise(scopeType, scopeCode);
        if (priceInPaise == null) {
            throw new IllegalArgumentException("Unknown " + scopeType.toLowerCase() + ": " + scopeCode);
        }

        BigDecimal amount = BigDecimal.valueOf(priceInPaise);
        String orderId = generateOrderId();
        Instant expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES);

        // Free (price 0) — grant access immediately, no gateway round-trip needed; Razorpay
        // doesn't meaningfully process a ₹0 charge anyway.
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            Payment payment = Payment.builder()
                    .userId(userId).orderId(orderId).amount(BigDecimal.ZERO).currency("INR")
                    .plan(scopeType + ":" + scopeCode).durationDays(SCOPED_ACCESS_DURATION_DAYS)
                    .scopeType(scopeType).scopeCode(scopeCode)
                    .status(Payment.PaymentStatus.SUCCESS)
                    .expiresAt(expiresAt)
                    .build();
            paymentRepository.save(payment);
            grantAccess(userId, scopeType, scopeCode, orderId);
            log.info("Free scoped grant issued: userId={} scopeType={} scopeCode={}", userId, scopeType, scopeCode);

            return CreateScopedOrderResponse.builder()
                    .orderId(orderId).amount(amount).currency("INR")
                    .scopeType(scopeType).scopeCode(scopeCode)
                    .expiresAt(expiresAt).freeGrant(true)
                    .build();
        }

        String gatewayOrderId = gatewayService.createGatewayOrder(orderId, amount, "INR");

        Payment payment = Payment.builder()
                .userId(userId).orderId(orderId).gatewayOrderId(gatewayOrderId)
                .amount(amount).currency("INR")
                .plan(scopeType + ":" + scopeCode).durationDays(SCOPED_ACCESS_DURATION_DAYS)
                .scopeType(scopeType).scopeCode(scopeCode)
                .status(Payment.PaymentStatus.CREATED)
                .expiresAt(expiresAt)
                .build();
        paymentRepository.save(payment);
        log.info("Scoped payment order created: orderId={} userId={} scopeType={} scopeCode={} amount={}",
                orderId, userId, scopeType, scopeCode, amount);

        publishEvent(KAFKA_TOPIC_PAYMENT_EVENTS, Map.of(
                "event", "PAYMENT_ORDER_CREATED", "orderId", orderId, "userId", userId.toString(),
                "scopeType", scopeType, "scopeCode", scopeCode, "amount", amount,
                "gatewayOrderId", gatewayOrderId, "timestamp", Instant.now().toString()
        ));

        return CreateScopedOrderResponse.builder()
                .orderId(orderId).gatewayOrderId(gatewayOrderId).amount(amount).currency("INR")
                .scopeType(scopeType).scopeCode(scopeCode).expiresAt(expiresAt)
                .keyId(gatewayService.getKeyId()).freeGrant(false)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Access checks (category/group ownership)
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AccessCheckResponse checkAccess(UUID userId, String scopeType, String scopeCode) {
        List<AccessGrant> active = accessGrantRepository.findActive(
                userId, AccessGrant.ScopeType.valueOf(scopeType.toUpperCase()), scopeCode, Instant.now());
        if (active.isEmpty()) return AccessCheckResponse.builder().hasAccess(false).build();
        return AccessCheckResponse.builder().hasAccess(true).expiresAt(active.get(0).getExpiresAt()).build();
    }

    @Transactional(readOnly = true)
    public List<MyAccessGrantResponse> getMyActiveGrants(UUID userId) {
        return accessGrantRepository.findAllActiveForUser(userId, Instant.now()).stream()
                .map(g -> MyAccessGrantResponse.builder()
                        .scopeType(g.getScopeType().name()).scopeCode(g.getScopeCode())
                        .purchasedAt(g.getPurchasedAt()).expiresAt(g.getExpiresAt())
                        .build())
                .toList();
    }

    private void grantAccess(UUID userId, String scopeType, String scopeCode, String orderId) {
        AccessGrant grant = AccessGrant.builder()
                .userId(userId)
                .scopeType(AccessGrant.ScopeType.valueOf(scopeType.toUpperCase()))
                .scopeCode(scopeCode)
                .orderId(orderId)
                .expiresAt(Instant.now().plus(SCOPED_ACCESS_DURATION_DAYS, ChronoUnit.DAYS))
                .build();
        accessGrantRepository.save(grant);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Verify Payment
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public PaymentResponse verifyPayment(UUID userId, VerifyPaymentRequest req) {
        // Find payment record
        Payment payment = paymentRepository.findByOrderId(req.getOrderId())
                .orElseThrow(() -> new EntityNotFoundException("Payment order not found: " + req.getOrderId()));

        // Verify ownership
        if (!payment.getUserId().equals(userId)) {
            throw new SecurityException("Payment order does not belong to this user");
        }

        // Check order status
        if (payment.getStatus() == Payment.PaymentStatus.SUCCESS) {
            throw new IllegalStateException("Payment already verified successfully");
        }
        if (payment.getStatus() == Payment.PaymentStatus.FAILED) {
            throw new IllegalStateException("Payment has already failed");
        }
        if (payment.getStatus() == Payment.PaymentStatus.EXPIRED) {
            throw new IllegalStateException("Payment order has expired. Please create a new order.");
        }

        // Check expiry
        if (Instant.now().isAfter(payment.getExpiresAt())) {
            payment.setStatus(Payment.PaymentStatus.EXPIRED);
            paymentRepository.save(payment);
            throw new IllegalStateException("Payment order has expired. Please create a new order.");
        }

        // Verify signature
        boolean signatureValid;
        if (devMode && TEST_SUCCESS_SIGNATURE.equals(req.getGatewaySignature())) {
            // Dev/test mode: accept special test signature
            signatureValid = true;
            log.info("DEV MODE: accepting TEST_SUCCESS signature for orderId={}", req.getOrderId());
        } else {
            signatureValid = gatewayService.verifySignature(
                    payment.getGatewayOrderId(),
                    req.getGatewayPaymentId(),
                    req.getGatewaySignature()
            );
        }

        if (!signatureValid) {
            // Mark as failed
            payment.setStatus(Payment.PaymentStatus.FAILED);
            payment.setFailureReason("Payment signature verification failed");
            paymentRepository.save(payment);

            publishEvent(KAFKA_TOPIC_PAYMENT_EVENTS, Map.of(
                    "event",    "PAYMENT_FAILED",
                    "orderId",  payment.getOrderId(),
                    "userId",   userId.toString(),
                    "reason",   "Signature verification failed",
                    "timestamp", Instant.now().toString()
            ));

            throw new SecurityException("Payment signature verification failed. Payment has been marked as failed.");
        }

        // Mark payment as successful
        payment.setStatus(Payment.PaymentStatus.SUCCESS);
        payment.setGatewayPaymentId(req.getGatewayPaymentId());
        payment.setGatewaySignature(req.getGatewaySignature());
        if (req.getPaymentMethod() != null) payment.setPaymentMethod(req.getPaymentMethod());
        if (req.getUpiId() != null)          payment.setUpiId(req.getUpiId());

        paymentRepository.save(payment);
        log.info("Payment verified successfully: orderId={} userId={} plan={}", payment.getOrderId(), userId, payment.getPlan());

        if (payment.getScopeType() != null) {
            // Category/group-scoped purchase — grant 1-year access, NOT the global Pro/Basic
            // subscription (those are unrelated, independent mechanisms).
            grantAccess(userId, payment.getScopeType(), payment.getScopeCode(), payment.getOrderId());
            log.info("Access granted for userId={} scopeType={} scopeCode={}",
                    userId, payment.getScopeType(), payment.getScopeCode());
        } else {
            // Activate the original global subscription on user-service
            try {
                userServiceClient.activateSubscription(userId, payment.getPlan(), payment.getDurationDays());
                log.info("Subscription activated for userId={} plan={}", userId, payment.getPlan());
            } catch (Exception e) {
                // Log but do not fail the payment — subscription activation can be retried via Kafka consumer
                log.error("Failed to activate subscription for userId={} plan={} - will rely on Kafka consumer for retry",
                        userId, payment.getPlan(), e);
            }
        }

        // Publish success event
        publishEvent(KAFKA_TOPIC_PAYMENT_EVENTS, Map.of(
                "event",           "PAYMENT_SUCCESS",
                "orderId",         payment.getOrderId(),
                "userId",          userId.toString(),
                "plan",            payment.getPlan(),
                "durationDays",    payment.getDurationDays(),
                "amount",          payment.getAmount(),
                "gatewayPaymentId", req.getGatewayPaymentId(),
                "timestamp",       Instant.now().toString()
        ));

        return PaymentResponse.from(payment);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Payment History
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentHistory(UUID userId) {
        return paymentRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(PaymentResponse::from)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Get Single Payment
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(String orderId, UUID userId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Payment order not found: " + orderId));

        if (!payment.getUserId().equals(userId)) {
            throw new SecurityException("Access denied: payment does not belong to this user");
        }

        return PaymentResponse.from(payment);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Refund Payment
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public PaymentResponse refundPayment(String orderId, UUID userId, RefundRequest req) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Payment order not found: " + orderId));

        if (!payment.getUserId().equals(userId)) {
            throw new SecurityException("Access denied: payment does not belong to this user");
        }

        if (payment.getStatus() != Payment.PaymentStatus.SUCCESS) {
            throw new IllegalStateException("Only successful payments can be refunded. Current status: " + payment.getStatus());
        }

        if (payment.getGatewayPaymentId() == null) {
            throw new IllegalStateException("Cannot refund: no gateway payment ID on record");
        }

        // Initiate refund via gateway
        String refundId = gatewayService.initiateRefund(payment.getGatewayPaymentId(), payment.getAmount());

        // Update payment record
        payment.setStatus(Payment.PaymentStatus.REFUNDED);
        payment.setRefundId(refundId);
        payment.setRefundedAt(Instant.now());

        // Store refund reason in metadata
        String existingMetadata = payment.getMetadata() != null ? payment.getMetadata() : "{}";
        payment.setMetadata(existingMetadata.replace("}", ",\"refundReason\":\""
                + (req.getReason() != null ? req.getReason().replace("\"", "'") : "") + "\"}"));

        paymentRepository.save(payment);
        log.info("Payment refunded: orderId={} userId={} refundId={}", orderId, userId, refundId);

        // Publish refund event
        publishEvent(KAFKA_TOPIC_PAYMENT_EVENTS, Map.of(
                "event",    "PAYMENT_REFUNDED",
                "orderId",  payment.getOrderId(),
                "userId",   userId.toString(),
                "refundId", refundId,
                "plan",     payment.getPlan(),
                "amount",   payment.getAmount(),
                "reason",   req.getReason() != null ? req.getReason() : "",
                "timestamp", Instant.now().toString()
        ));

        return PaymentResponse.from(payment);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Webhook Handler
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public void handleWebhook(WebhookPayload webhookPayload, String razorpaySignature) {
        log.info("Processing webhook event: {}", webhookPayload.getEvent());

        String event = webhookPayload.getEvent();
        Map<String, Object> payload = webhookPayload.getPayload();

        if (payload == null) {
            log.warn("Webhook payload is null for event: {}", event);
            return;
        }

        switch (event) {
            case "payment.captured" -> handlePaymentCaptured(payload);
            case "payment.failed"   -> handlePaymentFailed(payload);
            case "refund.processed" -> handleRefundProcessed(payload);
            default -> log.info("Unhandled webhook event type: {}", event);
        }
    }

    private void handlePaymentCaptured(Map<String, Object> payload) {
        String gatewayPaymentId = extractString(payload, "payment", "entity", "id");
        String gatewayOrderId   = extractString(payload, "payment", "entity", "order_id");

        if (gatewayOrderId == null) {
            log.warn("Webhook payment.captured missing order_id");
            return;
        }

        paymentRepository.findByGatewayOrderId(gatewayOrderId).ifPresentOrElse(payment -> {
            if (payment.getStatus() != Payment.PaymentStatus.SUCCESS) {
                payment.setStatus(Payment.PaymentStatus.SUCCESS);
                payment.setGatewayPaymentId(gatewayPaymentId);
                paymentRepository.save(payment);
                log.info("Webhook: payment.captured processed for gatewayOrderId={}", gatewayOrderId);

                // Fire subscription activation via Kafka (idempotent consumer handles duplicates)
                publishEvent(KAFKA_TOPIC_PAYMENT_EVENTS, Map.of(
                        "event",           "PAYMENT_SUCCESS",
                        "orderId",         payment.getOrderId(),
                        "userId",          payment.getUserId().toString(),
                        "plan",            payment.getPlan(),
                        "durationDays",    payment.getDurationDays(),
                        "amount",          payment.getAmount(),
                        "gatewayPaymentId", gatewayPaymentId != null ? gatewayPaymentId : "",
                        "source",          "webhook",
                        "timestamp",       Instant.now().toString()
                ));
            }
        }, () -> log.warn("Webhook: payment.captured - no payment found for gatewayOrderId={}", gatewayOrderId));
    }

    private void handlePaymentFailed(Map<String, Object> payload) {
        String gatewayOrderId = extractString(payload, "payment", "entity", "order_id");
        String errorDesc      = extractString(payload, "payment", "entity", "error_description");

        if (gatewayOrderId == null) {
            log.warn("Webhook payment.failed missing order_id");
            return;
        }

        paymentRepository.findByGatewayOrderId(gatewayOrderId).ifPresentOrElse(payment -> {
            if (payment.getStatus() == Payment.PaymentStatus.CREATED
                    || payment.getStatus() == Payment.PaymentStatus.PENDING) {
                payment.setStatus(Payment.PaymentStatus.FAILED);
                payment.setFailureReason(errorDesc != null ? errorDesc : "Payment failed (webhook)");
                paymentRepository.save(payment);
                log.info("Webhook: payment.failed processed for gatewayOrderId={}", gatewayOrderId);
            }
        }, () -> log.warn("Webhook: payment.failed - no payment found for gatewayOrderId={}", gatewayOrderId));
    }

    private void handleRefundProcessed(Map<String, Object> payload) {
        String refundId        = extractString(payload, "refund", "entity", "id");
        String gatewayPaymentId = extractString(payload, "refund", "entity", "payment_id");

        if (gatewayPaymentId == null) {
            log.warn("Webhook refund.processed missing payment_id");
            return;
        }

        paymentRepository.findByGatewayPaymentId(gatewayPaymentId).ifPresentOrElse(payment -> {
            if (payment.getStatus() != Payment.PaymentStatus.REFUNDED) {
                payment.setStatus(Payment.PaymentStatus.REFUNDED);
                payment.setRefundId(refundId);
                payment.setRefundedAt(Instant.now());
                paymentRepository.save(payment);
                log.info("Webhook: refund.processed for gatewayPaymentId={} refundId={}", gatewayPaymentId, refundId);
            }
        }, () -> log.warn("Webhook: refund.processed - no payment found for gatewayPaymentId={}", gatewayPaymentId));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scheduled: Expire Stale Orders
    // ─────────────────────────────────────────────────────────────────────────

    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void expireStaleOrders() {
        List<Payment.PaymentStatus> expirableStatuses = List.of(
                Payment.PaymentStatus.CREATED,
                Payment.PaymentStatus.PENDING
        );

        List<Payment> stalePayments = paymentRepository.findByStatusInAndExpiresAtBefore(
                expirableStatuses, Instant.now());

        if (!stalePayments.isEmpty()) {
            stalePayments.forEach(p -> p.setStatus(Payment.PaymentStatus.EXPIRED));
            paymentRepository.saveAll(stalePayments);
            log.info("Expired {} stale payment orders", stalePayments.size());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Admin Methods
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PaymentResponse> getAllPayments() {
        return paymentRepository.findAll().stream()
                .map(PaymentResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getStats() {
        long totalTransactions = paymentRepository.count();
        long successCount      = paymentRepository.countByStatus(Payment.PaymentStatus.SUCCESS);
        long failedCount       = paymentRepository.countByStatus(Payment.PaymentStatus.FAILED);
        long refundedCount     = paymentRepository.countByStatus(Payment.PaymentStatus.REFUNDED);
        long createdCount      = paymentRepository.countByStatus(Payment.PaymentStatus.CREATED);
        long expiredCount      = paymentRepository.countByStatus(Payment.PaymentStatus.EXPIRED);
        BigDecimal totalRevenue = paymentRepository.sumSuccessfulRevenue();

        // Revenue by plan
        List<Object[]> rawRevenueByPlan = paymentRepository.revenueByPlan();
        Map<String, BigDecimal> revenueByPlan = new LinkedHashMap<>();
        for (Object[] row : rawRevenueByPlan) {
            revenueByPlan.put((String) row[0], (BigDecimal) row[1]);
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalRevenue",       totalRevenue);
        stats.put("totalTransactions",  totalTransactions);
        stats.put("successCount",       successCount);
        stats.put("failedCount",        failedCount);
        stats.put("refundedCount",      refundedCount);
        stats.put("createdCount",       createdCount);
        stats.put("expiredCount",       expiredCount);
        stats.put("revenueByPlan",      revenueByPlan);
        return stats;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private String generateOrderId() {
        String random6 = UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 6)
                .toUpperCase();
        return "PAY_" + System.currentTimeMillis() + "_" + random6;
    }

    private void publishEvent(String topic, Map<String, Object> event) {
        try {
            kafkaTemplate.send(topic, (String) event.get("orderId"), event);
            log.debug("Published Kafka event: {} to topic: {}", event.get("event"), topic);
        } catch (Exception e) {
            // Never fail the main transaction due to Kafka issues
            log.error("Failed to publish Kafka event: {} - {}", event.get("event"), e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private String extractString(Map<String, Object> payload, String... keys) {
        try {
            Object current = payload;
            for (String key : keys) {
                if (current instanceof Map) {
                    current = ((Map<String, Object>) current).get(key);
                } else {
                    return null;
                }
            }
            return current instanceof String ? (String) current : null;
        } catch (Exception e) {
            return null;
        }
    }
}
