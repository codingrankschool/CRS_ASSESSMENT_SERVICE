package com.codingrank.assessment.service;

import com.codingrank.assessment.dto.LeaderboardResponse;
import com.codingrank.assessment.model.Assessment;
import com.codingrank.assessment.model.Contest;
import com.codingrank.assessment.model.LeaderboardEntry;
import com.codingrank.assessment.model.Submission;
import com.codingrank.assessment.repository.AssessmentRepository;
import com.codingrank.assessment.repository.ContestRepository;
import com.codingrank.assessment.repository.LeaderboardEntryRepository;
import com.codingrank.assessment.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeaderboardService {

    private final AssessmentRepository assessmentRepository;
    private final ContestRepository contestRepository;
    private final SubmissionRepository submissionRepository;
    private final LeaderboardEntryRepository leaderboardEntryRepository;
    private final Optional<SimpMessagingTemplate> messagingTemplate;

    /**
     * Recalculates and updates the leaderboard for a contest.
     */
    public LeaderboardResponse recalculateLeaderboard(String contestId) {
        String title = "Contest " + contestId;
        Instant contestStart = Instant.EPOCH;
        Instant contestEnd = null;

        Optional<Assessment> assessmentOpt = assessmentRepository.findById(contestId);
        if (assessmentOpt.isPresent()) {
            Assessment a = assessmentOpt.get();
            title = a.getTitle();
            if (a.getStartTime() != null) contestStart = a.getStartTime();
            contestEnd = a.getEndTime();
        } else {
            Optional<Contest> contestOpt = contestRepository.findById(contestId);
            if (contestOpt.isEmpty()) {
                contestOpt = contestRepository.findByItemId(contestId);
            }
            if (contestOpt.isPresent()) {
                Contest c = contestOpt.get();
                if (c.getStartTime() != null) contestStart = c.getStartTime();
                contestEnd = c.getEndTime();
            }
        }

        List<Submission> submissions = submissionRepository.findByItemIdOrderByCreatedAtDesc(contestId);

        log.info("Starting leaderboard recalculation: contestId='{}' totalSubmissions={}",
                contestId, submissions.size());

        // Group submissions by user
        Map<String, List<Submission>> userSubmissions = submissions.stream()
                .collect(Collectors.groupingBy(Submission::getUserId));

        List<LeaderboardEntry> entries = new ArrayList<>();

        for (Map.Entry<String, List<Submission>> entry : userSubmissions.entrySet()) {
            String userId = entry.getKey();
            List<Submission> subs = entry.getValue();

            // Reverse to process chronological order
            Collections.reverse(subs);

            String userName = subs.get(0).getUserName() != null ? subs.get(0).getUserName() : "User " + userId;
            String userEmail = subs.get(0).getUserEmail();

            boolean isDisqualified = subs.stream().anyMatch(s ->
                    "DISQUALIFIED".equalsIgnoreCase(s.getStatus()) ||
                    (s.getMetadata() != null && Boolean.TRUE.equals(s.getMetadata().get("disqualified")))
            );

            int totalScore = 0;
            int problemsSolved = 0;
            long totalPenaltyMinutes = 0;
            Instant lastAcceptedTime = null;

            // Group by questionId to calculate ICPC penalty
            Map<String, List<Submission>> questionSubs = subs.stream()
                    .filter(s -> s.getQuestionId() != null)
                    .collect(Collectors.groupingBy(Submission::getQuestionId));

            for (Map.Entry<String, List<Submission>> qEntry : questionSubs.entrySet()) {
                List<Submission> qSubs = qEntry.getValue();
                int failedAttempts = 0;
                boolean solved = false;

                for (Submission s : qSubs) {
                    if (solved) continue; // ignore submissions after first AC

                    if ("PASSED".equalsIgnoreCase(s.getStatus())) {
                        solved = true;
                        problemsSolved++;
                        totalScore += (s.getScore() != null ? s.getScore() : 0);
                        lastAcceptedTime = s.getCreatedAt();

                        long elapsedMinutes = (s.getCreatedAt() != null)
                                ? Math.max(0, Duration.between(contestStart, s.getCreatedAt()).toMinutes())
                                : 0;
                        // ICPC Penalty: elapsed time + (20 mins * failed attempts)
                        totalPenaltyMinutes += elapsedMinutes + (failedAttempts * 20L);
                    } else {
                        failedAttempts++;
                        totalScore = Math.max(totalScore, (s.getScore() != null ? s.getScore() : 0));
                    }
                }
            }

            LeaderboardEntry lbEntry = leaderboardEntryRepository
                    .findByContestIdAndUserId(contestId, userId)
                    .orElse(LeaderboardEntry.builder().contestId(contestId).userId(userId).build());

            lbEntry.setUserName(userName);
            lbEntry.setUserEmail(userEmail);
            lbEntry.setTotalScore(totalScore);
            lbEntry.setProblemsSolved(problemsSolved);
            lbEntry.setTotalPenaltyMinutes(totalPenaltyMinutes);
            lbEntry.setDisqualified(isDisqualified);
            lbEntry.setLastAcceptedSubmissionTime(lastAcceptedTime);

            entries.add(lbEntry);
        }

        // Sort: non-disqualified first, higher problems solved first, higher score first, lower penalty first
        entries.sort((a, b) -> {
            if (a.isDisqualified() != b.isDisqualified()) {
                return a.isDisqualified() ? 1 : -1;
            }
            if (b.getProblemsSolved() != a.getProblemsSolved()) {
                return Integer.compare(b.getProblemsSolved(), a.getProblemsSolved());
            }
            if (b.getTotalScore() != a.getTotalScore()) {
                return Integer.compare(b.getTotalScore(), a.getTotalScore());
            }
            if (a.getTotalPenaltyMinutes() != b.getTotalPenaltyMinutes()) {
                return Long.compare(a.getTotalPenaltyMinutes(), b.getTotalPenaltyMinutes());
            }
            return 0;
        });

        // Assign ranks and save
        for (int i = 0; i < entries.size(); i++) {
            entries.get(i).setRankPosition(i + 1);
        }
        leaderboardEntryRepository.saveAll(entries);

        LeaderboardResponse response = mapToResponse(contestId, title, contestStart, contestEnd, entries);

        // Broadcast over WebSocket if template present
        messagingTemplate.ifPresent(template -> {
            try {
                template.convertAndSend("/topic/contest/" + contestId + "/leaderboard", response);
            } catch (Exception e) {
                log.warn("Failed to broadcast leaderboard WebSocket event", e);
            }
        });

        return response;
    }

    public LeaderboardResponse getLeaderboard(String contestId) {
        String title = "Contest " + contestId;
        Instant contestStart = Instant.EPOCH;
        Instant contestEnd = null;

        Optional<Assessment> assessmentOpt = assessmentRepository.findById(contestId);
        if (assessmentOpt.isPresent()) {
            title = assessmentOpt.get().getTitle();
            contestStart = assessmentOpt.get().getStartTime();
            contestEnd = assessmentOpt.get().getEndTime();
        }

        List<LeaderboardEntry> entries = leaderboardEntryRepository.findByContestIdOrderByRankPositionAsc(contestId);
        return mapToResponse(contestId, title, contestStart, contestEnd, entries);
    }

    private LeaderboardResponse mapToResponse(String contestId, String title, Instant start, Instant end, List<LeaderboardEntry> entries) {
        List<LeaderboardResponse.RankItem> ranks = entries.stream()
                .map(e -> LeaderboardResponse.RankItem.builder()
                        .rank(e.getRankPosition())
                        .userId(e.getUserId())
                        .userName(e.getUserName())
                        .avatar(e.getAvatar())
                        .totalScore(e.getTotalScore())
                        .problemsSolved(e.getProblemsSolved())
                        .penaltyMinutes(e.getTotalPenaltyMinutes())
                        .isDisqualified(e.isDisqualified())
                        .lastSubmissionTime(e.getLastAcceptedSubmissionTime())
                        .build())
                .collect(Collectors.toList());

        return LeaderboardResponse.builder()
                .contestId(contestId)
                .title(title)
                .startTime(start)
                .endTime(end)
                .totalParticipants(ranks.size())
                .rankings(ranks)
                .build();
    }
}
