package com.codingrank.assessment.model;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Document(collection = "CheatEvent")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheatEvent {

    @Id
    private String id;

    @Indexed
    private String userId;

    @Indexed
    private String itemId;

    private String questionId;
    private String submissionId;

    private String eventType; // TAB_SWITCH, WINDOW_BLUR, FULLSCREEN_EXIT, COPY_PASTE, MULTIPLE_DISPLAYS

    @Builder.Default
    private Map<String, Object> details = new HashMap<>();

    @Builder.Default
    private String source = "client";

    @CreatedDate
    private Instant createdAt;
}
