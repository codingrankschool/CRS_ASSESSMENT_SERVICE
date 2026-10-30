package com.codingrank.assessment.service;

import com.codingrank.assessment.model.Quiz;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class QuizEvaluatorServiceTest {

    private QuizEvaluatorService evaluatorService;

    @BeforeEach
    void setUp() {
        evaluatorService = new QuizEvaluatorService(new ObjectMapper());
    }

    @Test
    void testMCQEvaluation() {
        Quiz.QuizQuestionItem mcq = Quiz.QuizQuestionItem.builder()
                .id("q1")
                .type("MCQ")
                .points(10)
                .options(List.of(
                        Map.of("id", "opt_a", "text", "Option A", "isCorrect", false),
                        Map.of("id", "opt_b", "text", "Option B", "isCorrect", true)
                ))
                .config(Map.of("correctOptionIds", List.of("opt_b")))
                .build();

        // Student selects opt_b
        Map<String, Object> answers = Map.of("q1", Map.of("selectedOptionIds", List.of("opt_b")));
        QuizEvaluatorService.QuizEvaluationResult result = evaluatorService.evaluate(List.of(mcq), null, answers);

        assertEquals(10, result.getTotalScore());
        assertEquals(1, result.getCorrectCount());
        assertTrue((Boolean) result.getQuestionResults().get(0).get("correct"));
    }

    @Test
    void testTrueFalseEvaluation() {
        Quiz.QuizQuestionItem tf = Quiz.QuizQuestionItem.builder()
                .id("q2")
                .type("TRUE_FALSE")
                .points(5)
                .config(Map.of("correctValue", true))
                .build();

        Map<String, Object> answers = Map.of("q2", Map.of("value", true));
        QuizEvaluatorService.QuizEvaluationResult result = evaluatorService.evaluate(List.of(tf), null, answers);

        assertEquals(5, result.getTotalScore());
        assertEquals(1, result.getCorrectCount());
    }

    @Test
    void testOutputPredictionEvaluation() {
        Quiz.QuizQuestionItem op = Quiz.QuizQuestionItem.builder()
                .id("q3")
                .type("OUTPUT_PREDICTION")
                .points(10)
                .config(Map.of("expectedOutput", "Hello World\n"))
                .build();

        Map<String, Object> answers = Map.of("q3", Map.of("output", "Hello World"));
        QuizEvaluatorService.QuizEvaluationResult result = evaluatorService.evaluate(List.of(op), null, answers);

        assertEquals(10, result.getTotalScore());
        assertEquals(1, result.getCorrectCount());
    }

    @Test
    void testFillInTheBlanksEvaluation() {
        Quiz.QuizQuestionItem fitb = Quiz.QuizQuestionItem.builder()
                .id("q4")
                .type("FILL_IN_THE_BLANKS")
                .points(10)
                .config(Map.of("blanks", List.of(
                        Map.of("correctValues", List.of("const", "let")),
                        Map.of("correctValues", List.of("function"))
                )))
                .build();

        Map<String, Object> answers = Map.of("q4", Map.of("values", List.of("const", "function")));
        QuizEvaluatorService.QuizEvaluationResult result = evaluatorService.evaluate(List.of(fitb), null, answers);

        assertEquals(10, result.getTotalScore());
        assertEquals(1, result.getCorrectCount());
    }

    @Test
    void testPlaygroundQuizzesRawJsonPayload() {
        Quiz.QuizQuestionItem mcq = Quiz.QuizQuestionItem.builder()
                .id("q1")
                .type("MCQ")
                .points(10)
                .config(Map.of("correctOptionIds", List.of("opt_1")))
                .build();

        String rawJson = """
                {
                   "questions": [
                      { "id": "q1", "response": { "selectedOptionIds": ["opt_1"] } }
                   ]
                }
                """;

        QuizEvaluatorService.QuizEvaluationResult result = evaluatorService.evaluate(List.of(mcq), rawJson, null);
        assertEquals(10, result.getTotalScore());
        assertEquals(1, result.getCorrectCount());
    }

    @Test
    void testLegacyNumericIndexMCQ() {
        Quiz.QuizQuestionItem mcq = Quiz.QuizQuestionItem.builder()
                .id("q1")
                .type("MCQ")
                .correctOption(2) // 3rd option is correct
                .points(10)
                .build();

        Map<String, Object> answers = Map.of("q1", 2);
        QuizEvaluatorService.QuizEvaluationResult result = evaluatorService.evaluate(List.of(mcq), null, answers);

        assertEquals(10, result.getTotalScore());
        assertEquals(1, result.getCorrectCount());
    }
}
