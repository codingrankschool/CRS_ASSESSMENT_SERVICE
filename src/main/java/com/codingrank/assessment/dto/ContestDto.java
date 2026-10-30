package com.codingrank.assessment.dto;

import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContestDto {
    private String id;
    private String itemId;
    private String title;
    private String description;
    private Instant startTime;
    private Instant endTime;
    private String status; // UPCOMING, ACTIVE, COMPLETED
    private boolean proctoring;
    private int problemCount;
    @Builder.Default
    private List<Object> problems = new ArrayList<>();
}
