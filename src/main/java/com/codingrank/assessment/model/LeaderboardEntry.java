package com.codingrank.assessment.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "LeaderboardEntry")
@CompoundIndexes({
    @CompoundIndex(name = "contest_user_uniq", def = "{'contestId': 1, 'userId': 1}", unique = true),
    @CompoundIndex(name = "contest_rank_idx", def = "{'contestId': 1, 'rankPosition': 1}")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaderboardEntry {

    @Id
    private String id;

    @Indexed
    private String contestId;

    @Indexed
    private String userId;

    private String userName;
    private String userEmail;
    private String avatar;

    @Builder.Default
    private int totalScore = 0;

    @Builder.Default
    private int problemsSolved = 0;

    @Builder.Default
    private long totalPenaltyMinutes = 0;

    private int rankPosition;

    @Builder.Default
    private boolean disqualified = false;

    private Instant lastAcceptedSubmissionTime;

    @LastModifiedDate
    private Instant updatedAt;
}
