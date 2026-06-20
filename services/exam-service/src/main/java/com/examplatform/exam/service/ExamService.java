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
    private final ExamCategoryRepository categoryRepository;
    private final ExamSlotRepository slotRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String SLOT_LOCK_KEY = "exam:slot:lock:%s";
    private static final String ENROLL_CACHE_KEY = "exam:enrolled:%s:%s"; // studentId:examId

    @Transactional
    public ExamResponse createExam(CreateExamRequest req, UUID adminId) {
        if (!categoryRepository.existsById(req.getCategory())) {
            throw new IllegalArgumentException("Unknown category: " + req.getCategory());
        }

        int totalQuestions = req.getSections() == null ? 0 :
                req.getSections().stream().mapToInt(CreateExamRequest.SectionDto::getMaxQuestions).sum();

        Exam exam = Exam.builder()
                .title(req.getTitle())
                .description(req.getDescription())
                .examType(req.getExamType())
                .categoryCode(req.getCategory())
                .testType(req.getTestType())
                .totalQuestions(totalQuestions)
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

    /**
     * Ensure the student has an attempt record for this (slot-less) practice exam,
     * creating a lightweight enrollment on first attempt and incrementing the
     * attempt counter on re-attempts. Used directly by the "Attempt" / "Re-attempt"
     * flow — no slot booking required.
     */
    @Transactional
    public AttemptResponse ensureAttempt(UUID examId, UUID studentId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new RuntimeException("Exam not found: " + examId));

        if (exam.getStatus() != Exam.ExamStatus.PUBLISHED
                && exam.getStatus() != Exam.ExamStatus.LIVE) {
            throw new IllegalStateException("Exam is not open for attempts");
        }

        Enrollment enrollment = enrollmentRepository
                .findByStudentIdAndExamId(studentId, examId)
                .orElse(null);

        boolean reattempt;
        if (enrollment == null) {
            enrollment = Enrollment.builder()
                    .studentId(studentId)
                    .examId(examId)
                    .slot(null)                       // slot-less practice attempt
                    .rollNumber(generateRollNumber(examId, studentId))
                    .status(Enrollment.EnrollmentStatus.APPEARED)
                    .attemptCount(1)
                    .build();
            reattempt = false;
        } else {
            enrollment.setAttemptCount(enrollment.getAttemptCount() + 1);
            reattempt = true;
        }
        enrollment = enrollmentRepository.save(enrollment);

        return AttemptResponse.builder()
                .enrollmentId(enrollment.getId())
                .examId(examId)
                .attemptNo(enrollment.getAttemptCount())
                .durationMins(exam.getDurationMins())
                .reattempt(reattempt)
                .build();
    }

    /**
     * List exams filtered by category + test type + status.
     * If studentId is provided, marks which exams the student has already attempted.
     */
    public List<ExamResponse> listExams(String category,
                                        com.examplatform.exam.model.TestType testType,
                                        Exam.ExamStatus status,
                                        UUID studentId) {
        List<Exam> exams = examRepository.filter(
                (category == null || category.isBlank()) ? null : category,
                testType, status);

        Set<UUID> attempted = studentId == null ? Set.of()
                : new HashSet<>(enrollmentRepository.findExamIdsByStudentId(studentId));

        return exams.stream().map(e -> {
            ExamResponse r = toResponse(e);
            r.setAttempted(attempted.contains(e.getId()));
            return r;
        }).toList();
    }

    /** Exams created by a given admin. SUPER_ADMIN sees all exams. */
    public List<ExamResponse> listByAdmin(UUID adminId, boolean isSuperAdmin) {
        List<Exam> exams = isSuperAdmin
                ? examRepository.findAllByOrderByCreatedAtDesc()
                : examRepository.findByCreatedByOrderByCreatedAtDesc(adminId);
        return exams.stream().map(this::toResponse).toList();
    }

    /** Aggregate analytics for the super-admin dashboard. */
    public Map<String, Object> analytics() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (Object[] row : examRepository.countByStatus())
            byStatus.put(String.valueOf(row[0]), ((Number) row[1]).longValue());

        Map<String, Long> byCategory = new LinkedHashMap<>();
        for (Object[] row : examRepository.countByCategory())
            byCategory.put(row[0] == null ? "UNCATEGORISED" : String.valueOf(row[0]),
                           ((Number) row[1]).longValue());

        return Map.of(
                "totalExams",       examRepository.count(),
                "totalEnrollments", enrollmentRepository.count(),
                "examsByStatus",    byStatus,
                "examsByCategory",  byCategory
        );
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
                .description(exam.getDescription())
                .examType(exam.getExamType())
                .category(exam.getCategoryCode())
                .testType(exam.getTestType() == null ? null : exam.getTestType().name())
                .totalQuestions(exam.getTotalQuestions())
                .durationMins(exam.getDurationMins())
                .totalMarks(exam.getTotalMarks())
                .status(exam.getStatus().name())
                .startTime(exam.getStartTime())
                .endTime(exam.getEndTime())
                .instructions(exam.getInstructions())
                .build();
    }
}
