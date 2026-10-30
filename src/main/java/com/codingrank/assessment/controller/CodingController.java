package com.codingrank.assessment.controller;

import com.codingrank.assessment.dto.ApiResponse;
import com.codingrank.assessment.dto.ExecuteCodeRequest;
import com.codingrank.assessment.service.CodingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/coding")
@RequiredArgsConstructor
public class CodingController {

    private final CodingService codingService;

    @PostMapping("/execute")
    public ResponseEntity<ApiResponse<Map<String, Object>>> executeCode(
            @RequestBody ExecuteCodeRequest request
    ) {
        Map<String, Object> result = codingService.executeCode(request);
        return ResponseEntity.ok(ApiResponse.ok("Code executed successfully", result));
    }
}
