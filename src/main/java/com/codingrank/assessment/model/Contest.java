package com.codingrank.assessment.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "Contest")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Contest {

    @Id
    private String id;

    @Indexed(unique = true)
    private String itemId;

    private Instant startTime;
    private Instant endTime;

    @Builder.Default
    private List<Object> problems = new ArrayList<>();

    @Builder.Default
    private boolean proctoring = true;
}
