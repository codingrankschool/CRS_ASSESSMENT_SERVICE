package com.codingrank.assessment.repository;

import com.codingrank.assessment.model.Submission;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubmissionRepository extends MongoRepository<Submission, String> {
    List<Submission> findByUserIdOrderByCreatedAtDesc(String userId);
    List<Submission> findByItemIdOrderByCreatedAtDesc(String itemId);
    List<Submission> findByItemIdAndUserId(String itemId, String userId);
    List<Submission> findByItemIdAndQuestionId(String itemId, String questionId);
}
