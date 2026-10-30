package com.codingrank.assessment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitQuizRequest {
    /**
     * ID of the assessment or quiz (accepts assessmentId or itemId from frontend)
     */
    private String assessmentId;
    private String itemId;

    private String questionId;
    private String batchId;
    private String courseId;

    /**
     * Single question answer or full quiz answer payload (JSON string, Map, or List)
     */
    private Object answer;

    /**
     * Map of quizQuestionId -> response/option
     */
    private Map<String, Object> answers;

    private Map<String, Object> metadata;

    @Builder.Default
    private int warningsCount = 0;

    @Builder.Default
    private boolean proctoredDisqualified = false;

    public String getEffectiveAssessmentId() {
        if (assessmentId != null && !assessmentId.isBlank()) return assessmentId;
        if (itemId != null && !itemId.isBlank()) return itemId;
        return "";
    }
}
