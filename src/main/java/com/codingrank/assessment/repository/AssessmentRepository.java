package com.codingrank.assessment.repository;

import com.codingrank.assessment.common.enums.AssessmentType;
import com.codingrank.assessment.model.Assessment;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssessmentRepository extends MongoRepository<Assessment, String> {
    Optional<Assessment> findByRefId(String refId);
    Optional<Assessment> findByItemId(String itemId);
    List<Assessment> findByTypeOrderByCreatedAtDesc(AssessmentType type);
    List<Assessment> findByPublishedTrueOrderByCreatedAtDesc();
}
