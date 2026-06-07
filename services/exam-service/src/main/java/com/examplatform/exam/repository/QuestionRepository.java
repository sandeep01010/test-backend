package com.examplatform.exam.repository;

import com.examplatform.exam.model.Question;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionRepository extends MongoRepository<Question, String> {

    List<Question> findByExamTypeAndSubjectAndActiveTrue(String examType, String subject);

    List<Question> findByExamTypeAndSubjectAndDifficultyAndActiveTrue(
            String examType, String subject, String difficulty);

    Page<Question> findByExamTypeAndActiveTrueOrderByCreatedAtDesc(
            String examType, Pageable pageable);

    @Query("{ 'exam_type': ?0, 'subject': ?1, 'difficulty': ?2, 'is_active': true }")
    List<Question> findForPaperGeneration(String examType, String subject, String difficulty);

    @Query("{ 'exam_type': ?0, 'subject': { $in: ?1 }, 'is_active': true }")
    List<Question> findByExamTypeAndSubjects(String examType, List<String> subjects);

    long countByExamTypeAndSubjectAndActiveTrue(String examType, String subject);
}
