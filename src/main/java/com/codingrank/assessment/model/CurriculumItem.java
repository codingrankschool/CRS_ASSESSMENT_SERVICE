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

    private String category; // EVALUATION, ASSIGNMENT, PROJECT, LECTURE
    private String audience; // LINK for a shared test link
    private String parentItemId; // Set when this item is a section of a test

    @Builder.Default
    private int order = 0;

    // Languages this item is limited to; empty means every language is allowed.
    @Builder.Default
    private List<String> allowedLanguages = new ArrayList<>();

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
