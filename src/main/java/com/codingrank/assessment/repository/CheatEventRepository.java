package com.codingrank.assessment.repository;

import com.codingrank.assessment.model.CheatEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CheatEventRepository extends MongoRepository<CheatEvent, String> {
    List<CheatEvent> findByUserIdAndItemId(String userId, String itemId);
    List<CheatEvent> findByItemId(String itemId);
    long countByUserIdAndItemId(String userId, String itemId);
}
