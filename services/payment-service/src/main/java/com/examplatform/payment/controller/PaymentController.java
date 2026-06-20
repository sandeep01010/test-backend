package com.examplatform.payment.controller;

import com.examplatform.payment.dto.*;
import com.examplatform.payment.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    // ─────────────────────────────────────────────────────────────────────────
    // Student Endpoints
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * POST /payments/orders
     * Create a new payment order for a plan.
     * Returns gateway order details for the frontend to complete payment.
     */
    @PostMapping("/orders")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<CreateOrderResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            HttpServletRequest httpRequest) {

        UUID userId = extractUserId(httpRequest);
        log.info("Create order request: userId={} plan={}", userId, request.getPlan());
        CreateOrderResponse response = paymentService.createOrder(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /payments/verify
     * Verify a completed payment using gateway signature.
     * Activates the subscription on success.
     */
    @PostMapping("/verify")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<PaymentResponse> verifyPayment(
            @Valid @RequestBody VerifyPaymentRequest request,
            HttpServletRequest httpRequest) {

        UUID userId = extractUserId(httpRequest);
        log.info("Verify payment request: userId={} orderId={}", userId, request.getOrderId());
        PaymentResponse response = paymentService.verifyPayment(userId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /payments/history
     * Get the authenticated user's full payment history.
     */
    @GetMapping("/history")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<PaymentResponse>> getHistory(HttpServletRequest httpRequest) {
        UUID userId = extractUserId(httpRequest);
        List<PaymentResponse> history = paymentService.getPaymentHistory(userId);
        return ResponseEntity.ok(history);
    }

    /**
     * GET /payments/{orderId}
     * Get a specific payment by order ID (must belong to the authenticated user).
     */
    @GetMapping("/{orderId}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<PaymentResponse> getPayment(
            @PathVariable String orderId,
            HttpServletRequest httpRequest) {

        UUID userId = extractUserId(httpRequest);
        PaymentResponse response = paymentService.getPayment(orderId, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /payments/refund
     * Request a refund for a successful payment.
     */
    @PostMapping("/refund")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<PaymentResponse> refundPayment(
            @Valid @RequestBody RefundRequest request,
            HttpServletRequest httpRequest) {

        UUID userId = extractUserId(httpRequest);
        log.info("Refund request: userId={} orderId={}", userId, request.getOrderId());
        PaymentResponse response = paymentService.refundPayment(request.getOrderId(), userId, request);
        return ResponseEntity.ok(response);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Webhook Endpoint (PUBLIC — called by Razorpay servers)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * POST /payments/webhook
     * Razorpay webhook receiver. No authentication — Razorpay calls this directly.
     * Signature is verified using X-Razorpay-Signature header.
     */
    @PostMapping("/webhook")
    public ResponseEntity<Map<String, String>> handleWebhook(
            @RequestBody WebhookPayload payload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String razorpaySignature) {

        log.info("Webhook received: event={}", payload.getEvent());
        paymentService.handleWebhook(payload, razorpaySignature);
        return ResponseEntity.ok(Map.of("status", "received"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Admin Endpoints
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * GET /payments/admin/all
     * Get all payments (admin view — all users).
     */
    @GetMapping("/admin/all")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<PaymentResponse>> getAllPayments() {
        return ResponseEntity.ok(paymentService.getAllPayments());
    }

    /**
     * GET /payments/admin/stats
     * Get aggregated payment statistics.
     * Returns: totalRevenue, totalTransactions, successCount, failedCount,
     *          refundedCount, revenueByPlan
     */
    @GetMapping("/admin/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(paymentService.getStats());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private UUID extractUserId(HttpServletRequest request) {
        Object userIdAttr = request.getAttribute("userId");
        if (userIdAttr == null) {
            String userIdHeader = request.getHeader("X-User-Id");
            if (userIdHeader != null && !userIdHeader.isBlank()) {
                return UUID.fromString(userIdHeader);
            }
            throw new SecurityException("User ID not found in request context");
        }
        return UUID.fromString(userIdAttr.toString());
    }
}
