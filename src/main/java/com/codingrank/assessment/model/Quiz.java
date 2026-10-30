package com.codingrank.assessment.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Document(collection = "Quiz")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Quiz {

    @Id
    private String id;

    @Indexed(unique = true)
    private String itemId;

    @Builder.Default
    private List<QuizQuestionItem> questions = new ArrayList<>();

    private Integer timeLimit; // In minutes
    private Instant startTime;
    private Instant endTime;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QuizQuestionItem {
        private String id;
        private String question;
        private String title;
        private String type; // MCQ, MSQ, TRUE_FALSE, OUTPUT_PREDICTION, FILL_IN_THE_BLANKS, etc.

        @Builder.Default
        private List<Object> options = new ArrayList<>();

        private Integer correctOption; // Index of the correct option (0-based)

        private Integer points;
        private Integer marks;

        private String explanation;

        @Builder.Default
        private Map<String, Object> config = new HashMap<>();

        public int getEffectivePoints() {
            if (points != null && points > 0) return points;
            if (marks != null && marks > 0) return marks;
            return 10;
        }

        public String getEffectiveTitle() {
            if (title != null && !title.isBlank()) return title;
            if (question != null && !question.isBlank()) return question;
            return "Question " + (id != null ? id : "");
        }
    }
}
