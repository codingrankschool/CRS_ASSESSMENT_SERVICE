package com.codingrank.assessment.repository;

import com.codingrank.assessment.model.CurriculumItem;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CurriculumItemRepository extends MongoRepository<CurriculumItem, String> {
    List<CurriculumItem> findByType(String type);
    List<CurriculumItem> findByTypeIgnoreCase(String type);
    List<CurriculumItem> findByCourseId(String courseId);
}
