package com.codingrank.assessment.controller;

import com.codingrank.assessment.dto.ApiResponse;
import com.codingrank.assessment.dto.SubmitCodeRequest;
import com.codingrank.assessment.dto.SubmitQuizRequest;
import com.codingrank.assessment.model.CheatEvent;
import com.codingrank.assessment.model.Submission;
import com.codingrank.assessment.service.SubmissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;

    @PostMapping("/code")
    public ResponseEntity<ApiResponse<Submission>> submitCode(
            Authentication authentication,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestHeader(value = "X-User-Email", required = false) String headerUserEmail,
            @RequestHeader(value = "X-User-Name", required = false) String headerUserName,
            @Valid @RequestBody SubmitCodeRequest request
    ) {
        String userId = (authentication != null && authentication.getName() != null)
                ? authentication.getName()
                : (headerUserId != null ? headerUserId : "guest_user");

        Submission submission = submissionService.submitCode(userId, headerUserEmail, headerUserName, request);
        return ResponseEntity.ok(ApiResponse.ok("Code evaluated successfully", submission));
    }

    @PostMapping("/quiz")
    public ResponseEntity<ApiResponse<Submission>> submitQuiz(
            Authentication authentication,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestHeader(value = "X-User-Email", required = false) String headerUserEmail,
            @RequestHeader(value = "X-User-Name", required = false) String headerUserName,
            @Valid @RequestBody SubmitQuizRequest request
    ) {
        String userId = (authentication != null && authentication.getName() != null)
                ? authentication.getName()
                : (headerUserId != null ? headerUserId : "guest_user");

        Submission submission = submissionService.submitQuiz(userId, headerUserEmail, headerUserName, request);
        return ResponseEntity.ok(ApiResponse.ok("Quiz submitted successfully", submission));
    }

    @PostMapping("/cheat-event")
    public ResponseEntity<ApiResponse<CheatEvent>> reportCheatEvent(
            Authentication authentication,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestBody Map<String, Object> body
    ) {
        String userId = (authentication != null && authentication.getName() != null)
                ? authentication.getName()
                : (headerUserId != null ? headerUserId : (String) body.get("userId"));

        String itemId = (String) body.get("itemId");
        String questionId = (String) body.get("questionId");
        String submissionId = (String) body.get("submissionId");
        String eventType = (String) body.getOrDefault("eventType", "TAB_SWITCH");

        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) body.getOrDefault("details", Map.of());

        CheatEvent event = submissionService.recordCheatEvent(userId, itemId, questionId, submissionId, eventType, details);
        return ResponseEntity.ok(ApiResponse.ok("Proctoring event logged", event));
    }
}
