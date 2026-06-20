package com.examplatform.result.kafka;

import com.examplatform.result.service.EvaluationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Consumes EXAM_SUBMITTED events and triggers evaluation pipeline.
 *
 * AckMode: BATCH (Spring Kafka default with enable-auto-commit=false).
 * Offset is committed automatically after each batch of records is processed
 * without error. On exception the batch is retried.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExamEventConsumer {

    private final EvaluationService evaluationService;

    @KafkaListener(
        topics = "exam-events",
        groupId = "result-service-consumer"
    )
    public void handleExamEvent(
            @Payload Map<String, Object> event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        String eventType = (String) event.get("event");
        log.info("Received event: {} partition={} offset={}", eventType, partition, offset);

        try {
            if ("EXAM_SUBMITTED".equals(eventType)) {
                String sessionId = (String) event.get("sessionId");
                String examId    = (String) event.get("examId");
                evaluationService.evaluateSubmission(sessionId, examId);
            }
        } catch (Exception e) {
            log.error("Failed to process event {}: {}", eventType, e.getMessage(), e);
            // Re-throw so Spring Kafka retries / sends to DLQ
            throw e;
        }
    }
}
