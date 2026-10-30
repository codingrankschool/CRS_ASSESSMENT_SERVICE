package com.codingrank.assessment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitCodeRequest {
    @NotBlank(message = "Assessment ID is required")
    private String assessmentId;

    @NotBlank(message = "Question ID is required")
    private String questionId;

    @NotBlank(message = "Code is required")
    private String code;

    @NotBlank(message = "Language is required")
    private String language; // python, java, cpp, javascript

    private String batchId;
    private String courseId;

    @Builder.Default
    private int warningsCount = 0;

    @Builder.Default
    private boolean proctoredDisqualified = false;
}
