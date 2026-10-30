package com.codingrank.assessment.controller;

import com.codingrank.assessment.dto.ApiResponse;
import com.codingrank.assessment.dto.LaunchTokenDto;
import com.codingrank.assessment.model.Assessment;
import com.codingrank.assessment.service.AssessmentService;
import com.codingrank.assessment.service.LmsIntegrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/launch")
@RequiredArgsConstructor
public class LmsLaunchController {

    private final LmsIntegrationService lmsIntegrationService;
    private final AssessmentService assessmentService;

    @PostMapping("/validate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> validateLaunch(
            @RequestBody Map<String, String> request
    ) {
        String token = request.get("token");
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Token is required"));
        }

        LaunchTokenDto launchData = lmsIntegrationService.validateLaunchToken(token);
        Assessment assessment = assessmentService.getAssessmentByRefId(launchData.getAssessmentRefId())
                .orElse(null);

        Map<String, Object> sessionData = Map.of(
                "student", launchData,
                "assessment", assessment != null ? assessment : Map.of("refId", launchData.getAssessmentRefId()),
                "status", "SESSION_INITIALIZED"
        );

        return ResponseEntity.ok(ApiResponse.ok("Launch token validated successfully", sessionData));
    }
}
