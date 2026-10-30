package com.codingrank.assessment.repository;

import com.codingrank.assessment.model.TestCase;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TestCaseRepository extends MongoRepository<TestCase, String> {
    List<TestCase> findByQuestionId(String questionId);
    List<TestCase> findByQuestionIdAndIsHiddenFalse(String questionId);
}
