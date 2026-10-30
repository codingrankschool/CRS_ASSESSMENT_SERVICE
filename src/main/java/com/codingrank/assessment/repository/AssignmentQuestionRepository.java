package com.codingrank.assessment.repository;

import com.codingrank.assessment.model.AssignmentQuestion;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AssignmentQuestionRepository extends MongoRepository<AssignmentQuestion, String> {
    List<AssignmentQuestion> findByAssignmentIdOrderByOrderAsc(String assignmentId);
}
