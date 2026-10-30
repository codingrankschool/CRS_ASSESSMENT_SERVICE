package com.codingrank.assessment.controller;

import com.codingrank.assessment.dto.ApiResponse;
import com.codingrank.assessment.dto.ContestDto;
import com.codingrank.assessment.dto.LeaderboardResponse;
import com.codingrank.assessment.service.AssessmentService;
import com.codingrank.assessment.service.LeaderboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/contests")
@RequiredArgsConstructor
public class ContestController {

    private final AssessmentService assessmentService;
    private final LeaderboardService leaderboardService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ContestDto>>> getContests(
            @RequestParam(required = false) String status
    ) {
        List<ContestDto> contests = assessmentService.getAllContests(status);
        return ResponseEntity.ok(ApiResponse.ok(contests));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ContestDto>> getContestById(@PathVariable String id) {
        return assessmentService.getContestDetails(id)
                .map(c -> ResponseEntity.ok(ApiResponse.ok(c)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/leaderboard")
    public ResponseEntity<ApiResponse<LeaderboardResponse>> getLeaderboard(@PathVariable String id) {
        LeaderboardResponse leaderboard = leaderboardService.getLeaderboard(id);
        return ResponseEntity.ok(ApiResponse.ok(leaderboard));
    }
}
