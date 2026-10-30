package com.codingrank.assessment.repository;

import com.codingrank.assessment.model.Assignment;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AssignmentRepository extends MongoRepository<Assignment, String> {
    Optional<Assignment> findByItemId(String itemId);
}
