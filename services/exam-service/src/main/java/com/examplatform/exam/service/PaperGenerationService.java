package com.examplatform.exam.service;

import com.examplatform.exam.model.Exam;
import com.examplatform.exam.model.ExamSection;
import com.examplatform.exam.model.Question;
import com.examplatform.exam.repository.ExamRepository;
import com.examplatform.exam.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates a deterministic, unique paper per student.
 * Same student + same exam always produces the same question order.
 * Different students get different orders (anti-cheating).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaperGenerationService {

    private final ExamRepository examRepository;
    private final QuestionRepository questionRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String PAPER_CACHE_KEY = "exam:paper:%s:%s"; // examId:studentId
    private static final int CACHE_BUFFER_MINS = 60;

    /**
     * Generate or retrieve cached exam paper for a student.
     * Returns list of Question objects (without correct_answer for students).
     */
    @SuppressWarnings("unchecked")
    public List<PaperQuestion> getOrGeneratePaper(UUID examId, UUID studentId) {
        String cacheKey = String.format(PAPER_CACHE_KEY, examId, studentId);

        // Try cache first
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("Paper cache hit for student {} exam {}", studentId, examId);
            return (List<PaperQuestion>) cached;
        }

        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new RuntimeException("Exam not found: " + examId));

        List<PaperQuestion> paper = generatePaper(exam, studentId);

        // Cache for exam duration + buffer
        long ttlMinutes = exam.getDurationMins() + CACHE_BUFFER_MINS;
        redisTemplate.opsForValue().set(cacheKey, paper, Duration.ofMinutes(ttlMinutes));

        log.info("Generated paper for student {} exam {}: {} questions",
                studentId, examId, paper.size());
        return paper;
    }

    private List<PaperQuestion> generatePaper(Exam exam, UUID studentId) {
        // Deterministic seed: hash of (studentId + examId) ensures reproducibility
        long seed = (studentId.toString() + exam.getId().toString()).hashCode();
        Random rng = new Random(seed);

        List<PaperQuestion> allQuestions = new ArrayList<>();
        int position = 1;

        for (ExamSection section : exam.getSections().stream()
                .sorted(Comparator.comparingInt(ExamSection::getSectionOrder))
                .collect(Collectors.toList())) {

            List<Question> sectionQuestions = questionRepository
                    .findByExamTypeAndSubjectAndActiveTrue(exam.getExamType(), section.getSubject());

            // Shuffle deterministically per student
            Collections.shuffle(sectionQuestions, rng);

            // Pick required number of questions
            int count = Math.min(section.getMaxQuestions(), sectionQuestions.size());
            List<Question> selected = sectionQuestions.subList(0, count);

            for (Question q : selected) {
                PaperQuestion pq = toPaperQuestion(q, section, position++);
                allQuestions.add(pq);
            }
        }

        return allQuestions;
    }

    private PaperQuestion toPaperQuestion(Question q, ExamSection section, int position) {
        // Strip correct answer before sending to student
        List<PaperQuestion.OptionDto> options = null;
        if (q.getOptions() != null) {
            options = q.getOptions().stream()
                    .map(o -> new PaperQuestion.OptionDto(o.getId(), o.getText(), o.getImageUrl()))
                    .collect(Collectors.toList());
        }

        return PaperQuestion.builder()
                .questionId(q.getId())
                .position(position)
                .sectionId(section.getId().toString())
                .sectionName(section.getName())
                .subject(q.getSubject())
                .type(q.getType())
                .questionText(q.getQuestionText())
                .questionHtml(q.getQuestionHtml())
                .questionImageUrls(q.getQuestionImageUrls())
                .options(options)
                .marks(q.getMarks())
                .negativeMarks(q.getNegativeMarks())
                .build();
    }

    // DTO for paper question (no correct answer)
    @lombok.Data @lombok.Builder @lombok.NoArgsConstructor @lombok.AllArgsConstructor
    public static class PaperQuestion {
        private String questionId;
        private int position;
        private String sectionId;
        private String sectionName;
        private String subject;
        private String type;
        private String questionText;
        private String questionHtml;
        private List<String> questionImageUrls;
        private List<OptionDto> options;
        private double marks;
        private double negativeMarks;

        @lombok.Data @lombok.AllArgsConstructor
        public static class OptionDto {
            private String id;
            private String text;
            private String imageUrl;
        }
    }
}
