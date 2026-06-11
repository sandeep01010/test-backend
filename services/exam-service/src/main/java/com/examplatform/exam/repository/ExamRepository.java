package com.examplatform.exam.repository;

import com.examplatform.exam.model.Exam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface ExamRepository extends JpaRepository<Exam, UUID> {

    List<Exam> findByStatusOrderByStartTimeAsc(Exam.ExamStatus status);

    List<Exam> findByCreatedByOrderByCreatedAtDesc(UUID adminId);

    @Query("SELECT e FROM Exam e WHERE e.status = 'PUBLISHED' AND e.startTime > :now ORDER BY e.startTime ASC")
    List<Exam> findUpcomingExams(Instant now);

    // ── Filtered listing (category + test type optional) ────────────────────────
    @Query("""
           SELECT e FROM Exam e
           WHERE (:category IS NULL OR e.categoryCode = :category)
             AND (:testType IS NULL OR e.testType = :testType)
             AND (:status   IS NULL OR e.status   = :status)
           ORDER BY e.startTime ASC NULLS LAST, e.createdAt DESC
           """)
    List<Exam> filter(String category,
                      com.examplatform.exam.model.TestType testType,
                      Exam.ExamStatus status);

    // ── Dashboard summary aggregation ───────────────────────────────────────────
    @Query("""
           SELECT e.categoryCode, e.testType, COUNT(e)
           FROM Exam e
           WHERE e.status = 'PUBLISHED'
           GROUP BY e.categoryCode, e.testType
           """)
    List<Object[]> countPublishedByCategoryAndType();

    /** How many exams in each category this student has enrolled in. */
    @Query("""
           SELECT e.categoryCode, COUNT(DISTINCT en.examId)
           FROM Enrollment en, Exam e
           WHERE en.examId = e.id AND en.studentId = :studentId
           GROUP BY e.categoryCode
           """)
    List<Object[]> countAttemptedByCategory(UUID studentId);

    // ── Admin analytics ─────────────────────────────────────────────────────────
    @Query("SELECT e.status, COUNT(e) FROM Exam e GROUP BY e.status")
    List<Object[]> countByStatus();

    @Query("SELECT e.categoryCode, COUNT(e) FROM Exam e GROUP BY e.categoryCode")
    List<Object[]> countByCategory();
}
