package com.examplatform.exam.repository;

import com.examplatform.exam.model.ExamSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ExamSlotRepository extends JpaRepository<ExamSlot, UUID> {

    List<ExamSlot> findByExamIdAndActiveTrueOrderBySlotDateAscStartTimeAsc(UUID examId);
}
