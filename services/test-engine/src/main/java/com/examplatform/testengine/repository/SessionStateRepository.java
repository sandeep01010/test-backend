package com.examplatform.testengine.repository;

import com.examplatform.testengine.model.SessionState;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SessionStateRepository extends MongoRepository<SessionState, String> {

    List<SessionState> findByExamIdAndStatus(String examId, String status);

    List<SessionState> findByStudentIdAndStatus(String studentId, String status);
}
