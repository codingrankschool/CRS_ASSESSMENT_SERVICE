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
import java.util.Map;

@Document(collection = "Question")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Question {

    @Id
    private String id;

    private String title;
    private String description;
    private String hint;
    private String editorial;

    @Builder.Default
    private boolean isEditorialReleased = false;

    @Builder.Default
    private String difficulty = "Medium";

    @Builder.Default
    private List<String> tags = new ArrayList<>();

    @Indexed
    private String type; // JAVA, JS, PYTHON, etc.

    private String constraints;
    private String functionSignature;
    private String questionCategory;
    private String functionInputDescription;
    private String functionOutputDescription;

    private Map<String, Object> languageSignatures;
    private Map<String, Object> frontendConfig;

    @Builder.Default
    private int points = 100;

    @Builder.Default
    private int timeLimitMs = 2000;

    @Builder.Default
    private int memoryLimitMb = 128;

    private String starterCodeTemplate;

    @Builder.Default
    private List<TestCase> testCases = new ArrayList<>();

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
