package com.codingrank.assessment.controller;

import com.codingrank.assessment.common.enums.AssessmentType;
import com.codingrank.assessment.dto.ApiResponse;
import com.codingrank.assessment.model.Assessment;
import com.codingrank.assessment.model.Assignment;
import com.codingrank.assessment.model.Contest;
import com.codingrank.assessment.model.Quiz;
import com.codingrank.assessment.service.AssessmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/assessments")
@RequiredArgsConstructor
public class AssessmentController {

    private final AssessmentService assessmentService;

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> healthCheck() {
        return ResponseEntity.ok(ApiResponse.ok("Service is healthy and running", java.util.Map.of(
                "status", "UP",
                "service", "crs-assessment-service",
                "timestamp", java.time.Instant.now()
        )));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<com.codingrank.assessment.dto.AssessmentSummaryDto>>> getAllAssessments(
            @RequestParam(required = false) String type
    ) {
        List<com.codingrank.assessment.dto.AssessmentSummaryDto> list = assessmentService.getAllAssessments(type);
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Assessment>> getAssessmentById(@PathVariable String id) {
        return assessmentService.getAssessmentById(id)
                .map(a -> ResponseEntity.ok(ApiResponse.ok(a)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/ref/{refId}")
    public ResponseEntity<ApiResponse<Assessment>> getAssessmentByRefId(@PathVariable String refId) {
        return assessmentService.getAssessmentByRefId(refId)
                .map(a -> ResponseEntity.ok(ApiResponse.ok(a)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Assessment>> createAssessment(@RequestBody Assessment assessment) {
        Assessment created = assessmentService.createAssessment(assessment);
        return ResponseEntity.ok(ApiResponse.ok("Assessment created successfully", created));
    }

    @GetMapping("/contest/{itemId}")
    public ResponseEntity<ApiResponse<Contest>> getContest(@PathVariable String itemId) {
        return assessmentService.getContestByItemId(itemId)
                .map(c -> ResponseEntity.ok(ApiResponse.ok(c)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/quiz/{itemId}")
    public ResponseEntity<ApiResponse<Quiz>> getQuiz(@PathVariable String itemId) {
        return assessmentService.getQuizByItemId(itemId)
                .map(q -> ResponseEntity.ok(ApiResponse.ok(q)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/assignment/{itemId}")
    public ResponseEntity<ApiResponse<Assignment>> getAssignment(@PathVariable String itemId) {
        return assessmentService.getAssignmentByItemId(itemId)
                .map(a -> ResponseEntity.ok(ApiResponse.ok(a)))
                .orElse(ResponseEntity.notFound().build());
    }
}
