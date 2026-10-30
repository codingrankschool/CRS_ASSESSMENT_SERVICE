package com.codingrank.assessment.model;

import lombok.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizQuestion {

    private String id;
    private String questionText;
    private String title;
    private String type; // MCQ, MSQ, TRUE_FALSE, etc.

    @Builder.Default
    private List<Object> options = new ArrayList<>();

    private int correctOptionIndex;

    private Integer points;
    private Integer marks;

    private int orderIndex;

    @Builder.Default
    private Map<String, Object> config = new HashMap<>();

    public int getEffectivePoints() {
        if (points != null && points > 0) return points;
        if (marks != null && marks > 0) return marks;
        return 10;
    }
}
