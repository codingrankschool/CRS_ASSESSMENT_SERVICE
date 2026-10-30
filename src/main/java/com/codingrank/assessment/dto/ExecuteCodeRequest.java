package com.codingrank.assessment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecuteCodeRequest {
    private String code;
    private String language;
    private String functionName;
    private String questionId;
    private String assessmentId;
    private List<TestCaseItem> testCases;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestCaseItem {
        private String input;
        private String output;
        private boolean isCustom;
        private boolean isHidden;
    }
}
