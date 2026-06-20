package com.examplatform.payment.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Simulates Razorpay-compatible payment gateway API.
 * Replace with real Razorpay SDK (com.razorpay:razorpay-java) in production.
 *
 * Production integration points:
 *   - createGatewayOrder  → RazorpayClient.orders.create(...)
 *   - verifySignature     → Utils.verifyPaymentSignature(...)
 *   - initiateRefund      → RazorpayClient.payments.refund(paymentId, ...)
 */
@Slf4j
@Service
public class PaymentGatewayService {

    @Value("${payment.gateway.key-id:rzp_test_examforge}")
    private String keyId;

    @Value("${payment.gateway.key-secret:examforge_secret_key_2024}")
    private String keySecret;

    @Value("${payment.gateway.dev-mode:true}")
    private boolean devMode;

    /**
     * Creates a gateway order. Returns a gatewayOrderId like "order_<random16>".
     * In production, this calls the Razorpay Orders API and returns the real order_id.
     *
     * @param orderId  our internal order ID (used as receipt)
     * @param amount   amount in paise (smallest unit)
     * @param currency currency code e.g. "INR"
     * @return gateway order ID
     */
    public String createGatewayOrder(String orderId, BigDecimal amount, String currency) {
        // Simulate gateway order creation
        String gatewayOrderId = "order_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        log.info("Gateway order created: {} for internal order: {} amount: {} {}", gatewayOrderId, orderId, amount, currency);
        return gatewayOrderId;
    }

    /**
     * Verifies payment signature using HMAC-SHA256.
     * Razorpay signature = HMAC_SHA256(gatewayOrderId + "|" + gatewayPaymentId, keySecret)
     *
     * @param gatewayOrderId   the gateway order ID
     * @param gatewayPaymentId the gateway payment ID
     * @param signature        the signature to verify
     * @return true if signature is valid
     */
    public boolean verifySignature(String gatewayOrderId, String gatewayPaymentId, String signature) {
        try {
            String data = gatewayOrderId + "|" + gatewayPaymentId;
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    keySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            String expected = bytesToHex(hash);
            boolean valid = expected.equals(signature);
            if (!valid) {
                log.warn("Signature verification failed for gatewayOrderId: {}, gatewayPaymentId: {}",
                        gatewayOrderId, gatewayPaymentId);
            }
            return valid;
        } catch (Exception e) {
            log.error("Error verifying payment signature", e);
            return false;
        }
    }

    /**
     * Verifies a webhook signature.
     * Razorpay webhook signature = HMAC_SHA256(rawBody, webhookSecret)
     *
     * @param rawBody           raw request body as string
     * @param webhookSignature  signature from X-Razorpay-Signature header
     * @param webhookSecret     the webhook secret configured in Razorpay dashboard
     * @return true if webhook signature is valid
     */
    public boolean verifyWebhookSignature(String rawBody, String webhookSignature, String webhookSecret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            String expected = bytesToHex(hash);
            return expected.equals(webhookSignature);
        } catch (Exception e) {
            log.error("Error verifying webhook signature", e);
            return false;
        }
    }

    /**
     * Generates the expected HMAC signature for test/dev mode.
     * Used so the frontend/Postman can pass the correct signature without a real gateway.
     *
     * @param gatewayOrderId   the gateway order ID
     * @param gatewayPaymentId the gateway payment ID
     * @return the expected HMAC-SHA256 signature
     */
    public String generateTestSignature(String gatewayOrderId, String gatewayPaymentId) {
        try {
            String data = gatewayOrderId + "|" + gatewayPaymentId;
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    keySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            log.error("Error generating test signature", e);
            throw new RuntimeException("Failed to generate test signature", e);
        }
    }

    /**
     * Simulates initiating a refund. Returns refundId like "rfnd_<random12>".
     * In production, calls RazorpayClient.payments.refund(paymentId, refundRequest).
     *
     * @param gatewayPaymentId the payment ID to refund
     * @param amount           amount to refund in paise
     * @return refund ID from gateway
     */
    public String initiateRefund(String gatewayPaymentId, BigDecimal amount) {
        String refundId = "rfnd_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        log.info("Refund initiated: {} for payment: {} amount: {}", refundId, gatewayPaymentId, amount);
        return refundId;
    }

    public String getKeyId() {
        return keyId;
    }

    public boolean isDevMode() {
        return devMode;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
