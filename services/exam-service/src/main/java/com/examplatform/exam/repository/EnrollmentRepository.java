package com.examplatform.exam.repository;

import com.examplatform.exam.model.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {

    boolean existsByStudentIdAndExamId(UUID studentId, UUID examId);

    Optional<Enrollment> findByStudentIdAndExamId(UUID studentId, UUID examId);
}
