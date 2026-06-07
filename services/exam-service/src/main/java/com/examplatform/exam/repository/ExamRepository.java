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
}
