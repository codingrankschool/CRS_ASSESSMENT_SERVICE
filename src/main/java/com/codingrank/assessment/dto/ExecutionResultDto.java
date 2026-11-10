package com.codingrank.assessment.dto;

import com.codingrank.assessment.common.enums.SubmissionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionResultDto {
    private SubmissionStatus status;
    private int score;
    private int testCasesPassed;
    private int testCasesTotal;
    private long runtimeMs;
    private long memoryKb;
    private String compilerOutput;
    private List<TestCaseResult> testCaseDetails;
    /** Real values of the first failing hidden case. Internal: never save this or send it without checking policy. */
    private TestCaseResult firstFailedHiddenCase;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestCaseResult {
        private String input;
        private String expectedOutput;
        private String actualOutput;
        private boolean passed;
        private boolean hidden;
        private long runtimeMs;
    }
}
