package com.codingrank.assessment.repository;

import com.codingrank.assessment.model.Contest;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ContestRepository extends MongoRepository<Contest, String> {
    Optional<Contest> findByItemId(String itemId);
    List<Contest> findByEndTimeAfter(Instant now);
}
