package com.codingrank.assessment.model;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Document(collection = "Submission")
@CompoundIndexes({
    @CompoundIndex(name = "user_item_status_idx", def = "{'userId': 1, 'itemId': 1, 'status': 1}"),
    @CompoundIndex(name = "item_score_idx", def = "{'itemId': 1, 'score': -1}")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Submission {

    @Id
    private String id;

    @Indexed
    private String userId;

    private String userEmail;
    private String userName;

    @Indexed
    private String itemId; // Assessment / Contest / Assignment / Quiz ID

    @Indexed
    private String questionId; // Specific coding question ID if applicable

    private String code;
    private String language;
    private String answer; // For quiz / text answers

    @Builder.Default
    private Integer score = 0;

    private String jobId;

    @Builder.Default
    private String status = "PENDING"; // PENDING, PASSED, FAILED

    @Builder.Default
    private int testCasesPassed = 0;

    @Builder.Default
    private int testCasesTotal = 0;

    private Long runtimeMs;
    private Long memoryKb;
    private String compilerOutput;

    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    @CreatedDate
    private Instant createdAt;

    /** First failing hidden case, sent back once in practice only. Transient, so never stored in the database. */
    @org.springframework.data.annotation.Transient
    private com.codingrank.assessment.dto.ExecutionResultDto.TestCaseResult revealedFailedCase;
}
