package com.codingrank.assessment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaderboardResponse {
    private String contestId;
    private String title;
    private Instant startTime;
    private Instant endTime;
    private int totalParticipants;
    private List<RankItem> rankings;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RankItem {
        private int rank;
        private String userId;
        private String userName;
        private String avatar;
        private int totalScore;
        private int problemsSolved;
        private long penaltyMinutes;
        private boolean isDisqualified;
        private Instant lastSubmissionTime;
    }
}
