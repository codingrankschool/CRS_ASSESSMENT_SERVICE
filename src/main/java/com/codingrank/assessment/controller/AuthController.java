package com.codingrank.assessment.controller;

import com.codingrank.assessment.dto.ApiResponse;
import com.codingrank.assessment.dto.AuthResponse;
import com.codingrank.assessment.dto.LoginRequest;
import com.codingrank.assessment.dto.UserSummaryDto;
import com.codingrank.assessment.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.<AuthResponse>builder()
                .success(true)
                .message("Login successful")
                .data(response)
                .timestamp(Instant.now())
                .build());
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserSummaryDto>> getProfile(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(401).body(ApiResponse.<UserSummaryDto>builder()
                    .success(false)
                    .message("Unauthorized: No active session")
                    .timestamp(Instant.now())
                    .build());
        }

        String userId = authentication.getName();
        UserSummaryDto user = authService.getCurrentUser(userId);

        return ResponseEntity.ok(ApiResponse.<UserSummaryDto>builder()
                .success(true)
                .message("User profile retrieved")
                .data(user)
                .timestamp(Instant.now())
                .build());
    }
}
