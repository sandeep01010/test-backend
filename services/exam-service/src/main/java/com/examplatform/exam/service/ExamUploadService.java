package com.examplatform.exam.service;

import com.examplatform.exam.dto.ExcelUploadRequest;
import com.examplatform.exam.dto.ExcelUploadResponse;
import com.examplatform.exam.dto.ExamPreviewResponse;
import com.examplatform.exam.model.*;
import com.examplatform.exam.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrates Excel → Exam + Questions pipeline.
 *
 * Flow:
 * 1. Parse Excel (ExcelParserService)
 * 2. Bulk-save questions to MongoDB
 * 3. Create Exam record in PostgreSQL (status = PUBLISHED immediately)
 * 4. Return summary response
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExamUploadService {

    private final ExcelParserService   excelParser;
    private final QuestionRepository   questionRepository;
    private final ExamRepository       examRepository;
    private final ExamCategoryRepository categoryRepository;

    @Transactional
    public ExcelUploadResponse uploadExam(MultipartFile file, UUID adminId) throws IOException {

        // 1. Parse Excel
        ExcelParserService.ParseResult parsed = excelParser.parse(file);
        ExcelUploadRequest meta = parsed.meta();
        List<Question> questions = parsed.questions();

        if (questions.isEmpty()) {
            throw new IllegalArgumentException("Excel contains no questions. Check the QUESTIONS sheet.");
        }

        if (!categoryRepository.existsById(meta.getCategoryCode())) {
            throw new IllegalArgumentException("Unknown category: " + meta.getCategoryCode()
                    + ". Create it in Admin → Categories first.");
        }

        // 2. Derive sections from question subjects (preserving insertion order)
        Map<String, List<Question>> bySubject = new LinkedHashMap<>();
        for (Question q : questions) {
            bySubject.computeIfAbsent(q.getSubject() != null ? q.getSubject() : "GENERAL",
                    k -> new ArrayList<>()).add(q);
        }

        TestType testType;
        try {
            testType = TestType.valueOf(meta.getTestType().toUpperCase().replace(" ", "_"));
        } catch (Exception e) {
            testType = TestType.FULL_MOCK;
        }

        // 3. Save Exam to PostgreSQL first (as DRAFT) so we have its UUID
        Exam exam = Exam.builder()
                .title(meta.getTitle())
                .description(meta.getDescription())
                .examType(meta.getCategoryCode())
                .categoryCode(meta.getCategoryCode())
                .testType(testType)
                .totalQuestions(questions.size())
                .createdBy(adminId)
                .durationMins(meta.getDurationMins() > 0 ? meta.getDurationMins() : 180)
                .totalMarks(meta.getTotalMarks() > 0 ? meta.getTotalMarks() : computeTotalMarks(questions))
                .negativeMarks(1.0)
                .status(Exam.ExamStatus.DRAFT)   // stays DRAFT until admin approves
                .instructions(meta.getInstructions())
                .shuffleQuestions(false)
                .showResultImmediately(true)
                .build();

        int order = 0;
        for (Map.Entry<String, List<Question>> entry : bySubject.entrySet()) {
            ExamSection section = ExamSection.builder()
                    .exam(exam)
                    .name(capitalize(entry.getKey()))
                    .subject(entry.getKey())
                    .maxQuestions(entry.getValue().size())
                    .marksPerQ(entry.getValue().isEmpty() ? 4.0 : entry.getValue().get(0).getMarks())
                    .negativeMarks(entry.getValue().isEmpty() ? 1.0 : Math.abs(entry.getValue().get(0).getNegativeMarks()))
                    .sectionOrder(order++)
                    .build();
            exam.getSections().add(section);
        }

        Exam savedExam = examRepository.save(exam);
        String examIdStr = savedExam.getId().toString();

        // 4. Tag questions with examId + createdBy and bulk-save to MongoDB
        String adminStr = adminId.toString();
        questions.forEach(q -> {
            q.setExamId(examIdStr);
            q.setCreatedBy(adminStr);
        });
        questionRepository.saveAll(questions);

        log.info("Exam '{}' saved as DRAFT with {} questions by admin {}",
                savedExam.getTitle(), questions.size(), adminId);

        return ExcelUploadResponse.builder()
                .examId(savedExam.getId())
                .title(savedExam.getTitle())
                .categoryCode(savedExam.getCategoryCode())
                .testType(savedExam.getTestType().name())
                .questionsImported(questions.size())
                .status("DRAFT")
                .message("Paper uploaded as Draft. Preview and verify before publishing.")
                .build();
    }

    /** Approve (publish) a DRAFT exam. Creator or SUPER_ADMIN may approve. */
    @Transactional
    public void approveExam(UUID examId, UUID adminId, boolean isSuperAdmin) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new RuntimeException("Exam not found: " + examId));
        if (!isSuperAdmin && !exam.getCreatedBy().equals(adminId)) {
            throw new SecurityException("Only the exam creator can approve this paper.");
        }
        if (exam.getStatus() != Exam.ExamStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT exams can be approved.");
        }
        exam.setStatus(Exam.ExamStatus.PUBLISHED);
        examRepository.save(exam);
        log.info("Exam '{}' approved and published by admin {}", exam.getTitle(), adminId);
    }

    /** Delete a DRAFT/PUBLISHED exam and all its questions from MongoDB. Creator or SUPER_ADMIN may delete. */
    @Transactional
    public void deleteExam(UUID examId, UUID adminId, boolean isSuperAdmin) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new RuntimeException("Exam not found: " + examId));
        if (!isSuperAdmin && !exam.getCreatedBy().equals(adminId)) {
            throw new SecurityException("Only the exam creator can delete this paper.");
        }
        if (exam.getStatus() == Exam.ExamStatus.LIVE) {
            throw new IllegalStateException("Cannot delete a LIVE exam.");
        }
        long tagged = questionRepository.countByExamId(examId.toString());
        if (tagged > 0) {
            questionRepository.deleteByExamId(examId.toString());
        } else if (exam.getExamType() != null) {
            // Legacy: delete questions by examType + createdBy (best-effort)
            List<Question> legacy = questionRepository
                    .findByExamTypeAndCreatedByOrderBySubjectAscCreatedAtAsc(
                            exam.getExamType(), exam.getCreatedBy().toString());
            if (!legacy.isEmpty()) {
                int cap = exam.getTotalQuestions() > 0 ? exam.getTotalQuestions() : legacy.size();
                List<Question> toDelete = legacy.size() > cap ? legacy.subList(0, cap) : legacy;
                questionRepository.deleteAll(toDelete);
            }
        }
        examRepository.delete(exam);
        log.info("Exam '{}' deleted by admin {}", exam.getTitle(), adminId);
    }

    /** Build a full preview of an exam with all questions and correct answers. */
    public ExamPreviewResponse previewExam(UUID examId, UUID adminId, boolean isSuperAdmin) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new RuntimeException("Exam not found: " + examId));
        if (!isSuperAdmin && !exam.getCreatedBy().equals(adminId)) {
            throw new SecurityException("Access denied.");
        }

        List<Question> questions = questionRepository.findByExamIdOrderBySubjectAscCreatedAtAsc(examId.toString());

        // Fallback for papers uploaded before examId tracking existed:
        // fetch by examType + createdBy and take up to totalQuestions
        if (questions.isEmpty() && exam.getExamType() != null) {
            List<Question> legacy = questionRepository
                    .findByExamTypeAndCreatedByOrderBySubjectAscCreatedAtAsc(
                            exam.getExamType(), exam.getCreatedBy().toString());
            int cap = exam.getTotalQuestions() > 0 ? exam.getTotalQuestions() : legacy.size();
            questions = legacy.size() > cap ? legacy.subList(0, cap) : legacy;
        }

        // Group by subject, preserving order
        Map<String, List<Question>> bySubject = new LinkedHashMap<>();
        for (Question q : questions) {
            bySubject.computeIfAbsent(q.getSubject() != null ? q.getSubject() : "GENERAL",
                    k -> new ArrayList<>()).add(q);
        }

        List<ExamPreviewResponse.PreviewSection> sections = new ArrayList<>();
        int qNo = 1;
        for (Map.Entry<String, List<Question>> entry : bySubject.entrySet()) {
            List<ExamPreviewResponse.PreviewQuestion> pqs = new ArrayList<>();
            for (Question q : entry.getValue()) {
                List<ExamPreviewResponse.OptionDto> opts = null;
                if (q.getOptions() != null) {
                    opts = q.getOptions().stream()
                            .map(o -> ExamPreviewResponse.OptionDto.builder()
                                    .id(o.getId()).text(o.getText()).imageUrl(o.getImageUrl()).build())
                            .collect(Collectors.toList());
                }
                Double numMin = null, numMax = null;
                if (q.getCorrectRange() != null) {
                    numMin = q.getCorrectRange().getMin();
                    numMax = q.getCorrectRange().getMax();
                }
                pqs.add(ExamPreviewResponse.PreviewQuestion.builder()
                        .questionId(q.getId())
                        .qNo(qNo++)
                        .type(q.getType())
                        .subject(q.getSubject())
                        .topic(q.getTopic())
                        .chapter(q.getChapter())
                        .difficulty(q.getDifficulty())
                        .questionText(q.getQuestionText())
                        .questionHtml(q.getQuestionHtml())
                        .questionImageUrls(q.getQuestionImageUrls())
                        .options(opts)
                        .correctAnswer(q.getCorrectAnswer())
                        .correctAnswers(q.getCorrectAnswers())
                        .numericalMin(numMin)
                        .numericalMax(numMax)
                        .explanation(q.getExplanation())
                        .marks(q.getMarks())
                        .negativeMarks(q.getNegativeMarks())
                        .tags(q.getTags())
                        .build());
            }
            sections.add(ExamPreviewResponse.PreviewSection.builder()
                    .sectionName(capitalize(entry.getKey()))
                    .subject(entry.getKey())
                    .questions(pqs)
                    .build());
        }

        return ExamPreviewResponse.builder()
                .examId(exam.getId())
                .title(exam.getTitle())
                .categoryCode(exam.getCategoryCode())
                .testType(exam.getTestType() != null ? exam.getTestType().name() : "")
                .status(exam.getStatus().name())
                .durationMins(exam.getDurationMins())
                .totalMarks(exam.getTotalMarks())
                .totalQuestions(exam.getTotalQuestions())
                .instructions(exam.getInstructions())
                .sections(sections)
                .build();
    }

    private int computeTotalMarks(List<Question> questions) {
        return questions.stream().mapToInt(q -> (int) q.getMarks()).sum();
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.charAt(0) + s.substring(1).toLowerCase();
    }
}
