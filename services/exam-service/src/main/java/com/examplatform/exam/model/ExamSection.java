package com.examplatform.exam.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "exam_sections")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ExamSection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 100)
    private String subject;

    @Column(name = "max_questions", nullable = false)
    private int maxQuestions;

    @Column(name = "marks_per_q", nullable = false)
    private double marksPerQ;

    @Column(name = "negative_marks")
    private double negativeMarks;

    @Column(name = "section_order", nullable = false)
    private int sectionOrder;
}
