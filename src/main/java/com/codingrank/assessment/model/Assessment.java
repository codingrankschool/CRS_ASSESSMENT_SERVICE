package com.codingrank.assessment.model;

import com.codingrank.assessment.common.enums.AssessmentType;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "Assessment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Assessment {

    @Id
    private String id;

    @Indexed(unique = true)
    private String itemId;

    private String refId;
    private String title;
    private String description;

    @Builder.Default
    private AssessmentType type = AssessmentType.CONTEST;

    private Instant startTime;
    private Instant endTime;
    private Integer durationMinutes;

    @Builder.Default
    private boolean proctored = true;

    @Builder.Default
    private int maxScore = 100;

    @Builder.Default
    private boolean published = true;

    @Builder.Default
    private List<Question> codingQuestions = new ArrayList<>();

    @Builder.Default
    private List<QuizQuestion> quizQuestions = new ArrayList<>();

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
