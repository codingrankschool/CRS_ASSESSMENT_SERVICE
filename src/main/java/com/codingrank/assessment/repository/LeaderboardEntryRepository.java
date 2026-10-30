package com.codingrank.assessment.repository;

import com.codingrank.assessment.model.LeaderboardEntry;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LeaderboardEntryRepository extends MongoRepository<LeaderboardEntry, String> {
    Optional<LeaderboardEntry> findByContestIdAndUserId(String contestId, String userId);
    List<LeaderboardEntry> findByContestIdOrderByTotalScoreDescTotalPenaltyMinutesAsc(String contestId);
    List<LeaderboardEntry> findByContestIdOrderByRankPositionAsc(String contestId);
}
