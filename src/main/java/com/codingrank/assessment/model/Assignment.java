package com.codingrank.assessment.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "Assignment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Assignment {

    @Id
    private String id;

    @Indexed(unique = true)
    private String itemId;

    @Builder.Default
    private int totalMarks = 100;

    private Integer duration; // in minutes
    private Instant startTime;
    private Instant endTime;

    private String problemStatement;
    private String starterCode;
    @Builder.Default
    private String language = "javascript";
    private Instant deadline;

    @Builder.Default
    private List<AssignmentQuestionRef> questions = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AssignmentQuestionRef {
        private String questionId;
        private int order;
        private int marks;
    }
}
