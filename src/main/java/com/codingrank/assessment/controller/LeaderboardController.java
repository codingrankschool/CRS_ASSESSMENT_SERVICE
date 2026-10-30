package com.codingrank.assessment.controller;

import com.codingrank.assessment.dto.ApiResponse;
import com.codingrank.assessment.dto.LeaderboardResponse;
import com.codingrank.assessment.service.LeaderboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/leaderboards")
@RequiredArgsConstructor
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    @GetMapping("/{contestId}")
    public ResponseEntity<ApiResponse<LeaderboardResponse>> getLeaderboard(@PathVariable String contestId) {
        LeaderboardResponse response = leaderboardService.getLeaderboard(contestId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/{contestId}/recalculate")
    public ResponseEntity<ApiResponse<LeaderboardResponse>> recalculate(@PathVariable String contestId) {
        LeaderboardResponse response = leaderboardService.recalculateLeaderboard(contestId);
        return ResponseEntity.ok(ApiResponse.ok("Leaderboard updated", response));
    }
}
