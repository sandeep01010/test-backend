package com.examplatform.result.repository;

import com.examplatform.result.model.Result;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ResultRepository extends JpaRepository<Result, UUID> {

    Optional<Result> findByStudentIdAndExamId(UUID studentId, UUID examId);

    Optional<Result> findByExamIdAndStudentId(UUID examId, UUID studentId);

    List<Result> findByExamIdOrderByRankAsc(UUID examId);

    @Query("SELECT r FROM Result r WHERE r.examId = :examId ORDER BY r.totalScore DESC")
    List<Result> findByExamIdForRanking(UUID examId);
}
