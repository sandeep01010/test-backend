package com.examplatform.exam.repository;

import com.examplatform.exam.model.ExamCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExamCategoryRepository extends JpaRepository<ExamCategory, String> {

    List<ExamCategory> findByActiveTrueOrderByDisplayOrderAsc();

    List<ExamCategory> findAllByOrderByDisplayOrderAsc();
}
