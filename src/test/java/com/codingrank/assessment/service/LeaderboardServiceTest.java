package com.codingrank.assessment.service;

import com.codingrank.assessment.common.enums.AssessmentType;
import com.codingrank.assessment.dto.LeaderboardResponse;
import com.codingrank.assessment.model.Assessment;
import com.codingrank.assessment.model.LeaderboardEntry;
import com.codingrank.assessment.model.Submission;
import com.codingrank.assessment.repository.AssessmentRepository;
import com.codingrank.assessment.repository.ContestRepository;
import com.codingrank.assessment.repository.LeaderboardEntryRepository;
import com.codingrank.assessment.repository.SubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeaderboardServiceTest {

    @Mock
    private AssessmentRepository assessmentRepository;

    @Mock
    private ContestRepository contestRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private LeaderboardEntryRepository leaderboardEntryRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private LeaderboardService leaderboardService;

    @BeforeEach
    void setUp() {
        leaderboardService = new LeaderboardService(
                assessmentRepository,
                contestRepository,
                submissionRepository,
                leaderboardEntryRepository,
                Optional.empty()
        );
    }

    @Test
    void shouldRecalculateLeaderboardCorrectly() {
        String contestId = "cnt_101";
        Instant now = Instant.now();

        Assessment contest = Assessment.builder()
                .id(contestId)
                .title("Weekly Contest")
                .type(AssessmentType.CONTEST)
                .startTime(now.minusSeconds(3600))
                .build();

        Submission subAlice = Submission.builder()
                .id("sub_1")
                .itemId(contestId)
                .questionId("q1")
                .userId("u_alice")
                .userName("Alice")
                .status("PASSED")
                .score(100)
                .createdAt(now.minusSeconds(1800))
                .build();

        Submission subBob = Submission.builder()
                .id("sub_2")
                .itemId(contestId)
                .questionId("q1")
                .userId("u_bob")
                .userName("Bob")
                .status("FAILED")
                .score(0)
                .createdAt(now.minusSeconds(1200))
                .build();

        when(assessmentRepository.findById(contestId)).thenReturn(Optional.of(contest));
        when(submissionRepository.findByItemIdOrderByCreatedAtDesc(contestId))
                .thenReturn(List.of(subBob, subAlice));
        when(leaderboardEntryRepository.findByContestIdAndUserId(any(), any()))
                .thenReturn(Optional.empty());

        LeaderboardResponse response = leaderboardService.recalculateLeaderboard(contestId);

        assertNotNull(response);
        assertEquals(2, response.getRankings().size());

        LeaderboardResponse.RankItem rank1 = response.getRankings().get(0);
        assertEquals("Alice", rank1.getUserName());
        assertEquals(1, rank1.getRank());
        assertEquals(100, rank1.getTotalScore());
        assertEquals(1, rank1.getProblemsSolved());

        LeaderboardResponse.RankItem rank2 = response.getRankings().get(1);
        assertEquals("Bob", rank2.getUserName());
        assertEquals(2, rank2.getRank());
        assertEquals(0, rank2.getProblemsSolved());
    }
}
