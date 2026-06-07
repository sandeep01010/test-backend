package com.examplatform.exam.service;

import com.examplatform.exam.dto.*;
import com.examplatform.exam.model.Exam;
import com.examplatform.exam.model.ExamSection;
import com.examplatform.exam.model.ExamSlot;
import com.examplatform.exam.model.Enrollment;
import com.examplatform.exam.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExamService {

    private final ExamRepository examRepository;
    private final ExamSlotRepository slotRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String SLOT_LOCK_KEY = "exam:slot:lock:%s";
    private static final String ENROLL_CACHE_KEY = "exam:enrolled:%s:%s"; // studentId:examId

    @Transactional
    public ExamResponse createExam(CreateExamRequest req, UUID adminId) {
        Exam exam = Exam.builder()
                .title(req.getTitle())
                .description(req.getDescription())
                .examType(req.getExamType())
                .createdBy(adminId)
                .durationMins(req.getDurationMins())
                .totalMarks(req.getTotalMarks())
                .negativeMarks(req.getNegativeMarks())
                .status(Exam.ExamStatus.DRAFT)
                .instructions(req.getInstructions())
                .shuffleQuestions(req.isShuffleQuestions())
                .showResultImmediately(req.isShowResultImmediately())
                .build();

        exam = examRepository.save(exam);

        // Add sections
        if (req.getSections() != null) {
            int order = 1;
            for (CreateExamRequest.SectionDto s : req.getSections()) {
                ExamSection section = ExamSection.builder()
                        .exam(exam)
                        .name(s.getName())
                        .subject(s.getSubject())
                        .maxQuestions(s.getMaxQuestions())
                        .marksPerQ(s.getMarksPerQ())
                        .negativeMarks(s.getNegativeMarks())
                        .sectionOrder(order++)
                        .build();
                exam.getSections().add(section);
            }
            exam = examRepository.save(exam);
        }

        log.info("Exam created: {} by admin {}", exam.getId(), adminId);
        return toResponse(exam);
    }

    @Transactional
    public ExamResponse publishExam(UUID examId, UUID adminId, PublishExamRequest req) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new RuntimeException("Exam not found"));

        if (exam.getStatus() != Exam.ExamStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT exams can be published");
        }

        exam.setStartTime(req.getStartTime());
        exam.setEndTime(req.getEndTime());
        exam.setStatus(Exam.ExamStatus.PUBLISHED);
        exam = examRepository.save(exam);

        // Publish event — triggers pre-scaling CronJob
        kafkaTemplate.send("exam-events", examId.toString(),
                Map.of("event", "EXAM_PUBLISHED",
                       "examId", examId,
                       "startTime", req.getStartTime().toString(),
                       "expectedStudents", req.getExpectedStudents()));

        return toResponse(exam);
    }

    /**
     * Enroll student in an exam slot using distributed lock to prevent over-enrollment.
     */
    @Transactional
    public EnrollmentResponse enrollStudent(UUID examId, UUID studentId, UUID slotId) {
        // Check already enrolled
        if (enrollmentRepository.existsByStudentIdAndExamId(studentId, examId)) {
            throw new IllegalStateException("Already enrolled in this exam");
        }

        // Distributed lock on the slot to prevent race conditions
        String lockKey = String.format(SLOT_LOCK_KEY, slotId);
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", Duration.ofSeconds(10));

        if (!Boolean.TRUE.equals(locked)) {
            throw new RuntimeException("Slot is busy, please try again");
        }

        try {
            ExamSlot slot = slotRepository.findById(slotId)
                    .orElseThrow(() -> new RuntimeException("Slot not found"));

            if (!slot.isActive()) {
                throw new IllegalStateException("Slot is no longer available");
            }
            if (slot.getEnrolledCount() >= slot.getCapacity()) {
                throw new IllegalStateException("Slot is full");
            }

            // Increment enrollment count atomically
            slot.setEnrolledCount(slot.getEnrolledCount() + 1);
            slotRepository.save(slot);

            // Generate roll number
            String rollNumber = generateRollNumber(examId, studentId);

            Enrollment enrollment = Enrollment.builder()
                    .studentId(studentId)
                    .examId(examId)
                    .slot(slot)
                    .rollNumber(rollNumber)
                    .status(Enrollment.EnrollmentStatus.ENROLLED)
                    .build();
            enrollment = enrollmentRepository.save(enrollment);

            // Cache enrollment for fast verification
            String cacheKey = String.format(ENROLL_CACHE_KEY, studentId, examId);
            redisTemplate.opsForValue().set(cacheKey, slotId.toString(), Duration.ofHours(24));

            kafkaTemplate.send("enrollment-events", studentId.toString(),
                    Map.of("event", "STUDENT_ENROLLED", "enrollmentId", enrollment.getId(),
                           "studentId", studentId, "examId", examId, "slotId", slotId));

            return EnrollmentResponse.builder()
                    .enrollmentId(enrollment.getId())
                    .rollNumber(rollNumber)
                    .slotDate(slot.getSlotDate().toString())
                    .startTime(slot.getStartTime().toString())
                    .centerName(slot.getCenterName())
                    .build();
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    public ExamResponse getExam(UUID examId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new RuntimeException("Exam not found: " + examId));
        return toResponse(exam);
    }

    public List<SlotResponse> getAvailableSlots(UUID examId) {
        return slotRepository.findByExamIdAndActiveTrueOrderBySlotDateAscStartTimeAsc(examId)
                .stream()
                .map(s -> SlotResponse.builder()
                        .id(s.getId())
                        .slotDate(s.getSlotDate().toString())
                        .startTime(s.getStartTime().toString())
                        .endTime(s.getEndTime().toString())
                        .capacity(s.getCapacity())
                        .enrolledCount(s.getEnrolledCount())
                        .available(s.getCapacity() - s.getEnrolledCount())
                        .centerName(s.getCenterName())
                        .city(s.getCenterCity())
                        .full(s.getEnrolledCount() >= s.getCapacity())
                        .build())
                .toList();
    }

    private String generateRollNumber(UUID examId, UUID studentId) {
        String prefix = examId.toString().substring(0, 4).toUpperCase();
        String suffix = String.format("%08d", Math.abs(studentId.hashCode() % 100000000));
        return prefix + suffix;
    }

    private ExamResponse toResponse(Exam exam) {
        return ExamResponse.builder()
                .id(exam.getId())
                .title(exam.getTitle())
                .examType(exam.getExamType())
                .durationMins(exam.getDurationMins())
                .totalMarks(exam.getTotalMarks())
                .status(exam.getStatus().name())
                .startTime(exam.getStartTime())
                .endTime(exam.getEndTime())
                .build();
    }
}
