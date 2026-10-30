package com.codingrank.assessment.model;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "CurriculumItem")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CurriculumItem {

    @Id
    private String id;

    private String title;
    private String description;

    @Builder.Default
    private List<String> tags = new ArrayList<>();

    @Indexed
    private String courseId;

    @Indexed
    private String batchId;

    @Indexed
    private String unitId;

    @Indexed
    private String type; // CONTEST, ASSIGNMENT, QUIZ, ASSESSMENT

    @Builder.Default
    private int order = 0;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
