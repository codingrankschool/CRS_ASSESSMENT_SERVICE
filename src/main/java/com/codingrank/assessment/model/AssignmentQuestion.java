package com.codingrank.assessment.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "AssignmentQuestion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignmentQuestion {

    @Id
    private String id;

    @Indexed
    private String assignmentId;

    @Indexed
    private String questionId;

    private int order;
    private int marks;

    @Builder.Default
    private boolean isEditorialReleased = false;
}
