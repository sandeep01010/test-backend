package com.examplatform.payment.repository;

import com.examplatform.payment.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByOrderId(String orderId);

    Optional<Payment> findByGatewayOrderId(String gatewayOrderId);

    List<Payment> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<Payment> findByUserIdAndStatus(UUID userId, Payment.PaymentStatus status);

    List<Payment> findByStatusAndExpiresAtBefore(Payment.PaymentStatus status, Instant now);

    List<Payment> findByStatusInAndExpiresAtBefore(List<Payment.PaymentStatus> statuses, Instant now);

    @Query("SELECT COUNT(p) FROM Payment p WHERE p.status = :status")
    long countByStatus(@Param("status") Payment.PaymentStatus status);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.status = 'SUCCESS'")
    BigDecimal sumSuccessfulRevenue();

    @Query("SELECT p.plan, COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.status = 'SUCCESS' GROUP BY p.plan")
    List<Object[]> revenueByPlan();

    Optional<Payment> findByGatewayPaymentId(String gatewayPaymentId);

    @Query("SELECT p FROM Payment p WHERE p.userId = :userId ORDER BY p.createdAt DESC")
    List<Payment> findRecentByUserId(@Param("userId") UUID userId);
}
