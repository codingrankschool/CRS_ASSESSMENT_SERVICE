package com.codingrank.assessment.service;

import com.codingrank.assessment.dto.ExecuteCodeRequest;
import com.codingrank.assessment.dto.ExecutionResultDto;
import com.codingrank.assessment.model.Question;
import com.codingrank.assessment.model.TestCase;
import com.codingrank.assessment.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CodingService {

    private final CodeJudgeService codeJudgeService;
    private final QuestionRepository questionRepository;
    private final SubmissionService submissionService;

    public Map<String, Object> executeCode(ExecuteCodeRequest request) {
        Question question = null;
        if (request.getQuestionId() != null && !request.getQuestionId().isBlank()) {
            question = questionRepository.findById(request.getQuestionId()).orElse(null);
        }

        if (question == null) {
            question = Question.builder()
                    .id(request.getQuestionId() != null ? request.getQuestionId() : "adhoc_run")
                    .title("Code Run")
                    .functionSignature(request.getFunctionName())
                    .points(100)
                    .build();
        }

        List<TestCase> testCasesToRun = new ArrayList<>();
        if (request.getTestCases() != null && !request.getTestCases().isEmpty()) {
            for (ExecuteCodeRequest.TestCaseItem item : request.getTestCases()) {
                if (item.isHidden() || "<hidden>".equalsIgnoreCase(item.getInput())) {
                    continue; // Skip hidden placeholder test cases in adhoc Run
                }
                testCasesToRun.add(TestCase.builder()
                        .input(item.getInput())
                        .output(item.getOutput())
                        .isHidden(false)
                        .build());
            }
        }
        
        if (testCasesToRun.isEmpty()) {
            // Fallback to question's resolved test cases, keeping only visible ones with real input
            List<TestCase> resolved = submissionService.resolveTestCases(question, request.getAssessmentId());
            for (TestCase tc : resolved) {
                if (!tc.isHidden() && tc.getInput() != null && !tc.getInput().isBlank() && !"<hidden>".equalsIgnoreCase(tc.getInput())) {
                    testCasesToRun.add(tc);
                }
            }
            if (testCasesToRun.isEmpty() && !resolved.isEmpty()) {
                testCasesToRun = resolved;
            }
        }

        log.info("Adhoc code execution requested: questionId='{}' language='{}' testCases={}",
                question.getId(), request.getLanguage(), testCasesToRun.size());

        ExecutionResultDto evalResult = codeJudgeService.evaluate(
                question, testCasesToRun, request.getCode(), request.getLanguage()
        );

        Map<String, Object> responseData = new HashMap<>();
        String output = evalResult.getCompilerOutput();
        if (output == null || output.isBlank()) {
            output = evalResult.getStatus().name() + " (" + evalResult.getTestCasesPassed() + "/" + evalResult.getTestCasesTotal() + " passed)";
        }
        responseData.put("output", output);
        responseData.put("status", evalResult.getStatus().name());
        responseData.put("passedCount", evalResult.getTestCasesPassed());
        responseData.put("totalCount", evalResult.getTestCasesTotal());
        responseData.put("runtimeMs", evalResult.getRuntimeMs());

        List<Map<String, Object>> resultsList = new ArrayList<>();
        if (evalResult.getTestCaseDetails() != null) {
            for (ExecutionResultDto.TestCaseResult r : evalResult.getTestCaseDetails()) {
                Map<String, Object> tcMap = new HashMap<>();
                tcMap.put("passed", r.isPassed());
                tcMap.put("input", r.getInput());
                tcMap.put("actual", r.getActualOutput());
                tcMap.put("expected", r.getExpectedOutput());
                tcMap.put("runtime", r.getRuntimeMs());
                tcMap.put("error", r.isPassed() ? null : "Output mismatch");
                resultsList.add(tcMap);
            }
        }
        responseData.put("results", resultsList);

        return responseData;
    }
}
