package com.codingrank.assessment.dto;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssessmentSummaryDto {
    private String id;
    private String itemId;
    private String title;
    private String description;
    private String type; // CONTEST, QUIZ, ASSIGNMENT
    private String status; // UPCOMING, ACTIVE, COMPLETED
    private Instant startTime;
    private Instant endTime;
    private Integer durationMinutes;
    private boolean proctored;
    private int maxScore;
}
