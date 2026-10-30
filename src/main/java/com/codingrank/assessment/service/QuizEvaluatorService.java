package com.codingrank.assessment.service;

import com.codingrank.assessment.model.Quiz;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class QuizEvaluatorService {

    private final ObjectMapper objectMapper;

    @Data
    @Builder
    public static class QuizEvaluationResult {
        private int totalScore;
        private int maxScore;
        private int correctCount;
        private int totalQuestions;
        private List<Map<String, Object>> questionResults;
    }

    /**
     * Evaluates all question types across full quiz or individual question answers.
     */
    public QuizEvaluationResult evaluate(
            List<Quiz.QuizQuestionItem> questions,
            Object rawAnswer,
            Map<String, Object> directAnswers
    ) {
        if (questions == null || questions.isEmpty()) {
            return QuizEvaluationResult.builder()
                    .totalScore(0)
                    .maxScore(0)
                    .correctCount(0)
                    .totalQuestions(0)
                    .questionResults(Collections.emptyList())
                    .build();
        }

        Map<String, Object> responseMap = parseResponses(rawAnswer, directAnswers);
        List<Map<String, Object>> questionResults = new ArrayList<>();

        int totalScore = 0;
        int maxScore = 0;
        int correctCount = 0;

        for (int i = 0; i < questions.size(); i++) {
            Quiz.QuizQuestionItem q = questions.get(i);
            String qId = q.getId() != null && !q.getId().isBlank() ? q.getId() : ("q-" + (i + 1));
            int qMarks = q.getEffectivePoints();
            maxScore += qMarks;

            Object userResp = responseMap.get(qId);
            if (userResp == null && q.getQuestion() != null) {
                userResp = responseMap.get(q.getQuestion());
            }
            if (userResp == null && responseMap.containsKey("default")) {
                userResp = responseMap.get("default");
            }

            EvaluationSingleResult single = evaluateSingleQuestion(q, userResp, qMarks);
            totalScore += single.score;
            if (single.correct) {
                correctCount++;
            }

            Map<String, Object> qr = new HashMap<>();
            qr.put("questionId", qId);
            qr.put("type", q.getType() != null ? q.getType() : "MCQ");
            qr.put("score", single.score);
            qr.put("maxScore", qMarks);
            qr.put("correct", single.correct);
            qr.put("feedback", single.feedback);
            questionResults.add(qr);
        }

        return QuizEvaluationResult.builder()
                .totalScore(totalScore)
                .maxScore(maxScore)
                .correctCount(correctCount)
                .totalQuestions(questions.size())
                .questionResults(questionResults)
                .build();
    }

    private static class EvaluationSingleResult {
        int score;
        boolean correct;
        String feedback;

        EvaluationSingleResult(int score, boolean correct, String feedback) {
            this.score = score;
            this.correct = correct;
            this.feedback = feedback;
        }
    }

    private EvaluationSingleResult evaluateSingleQuestion(Quiz.QuizQuestionItem q, Object response, int marks) {
        if (response == null) {
            return new EvaluationSingleResult(0, false, "No answer provided");
        }

        String type = q.getType() != null ? q.getType().trim().toUpperCase() : "MCQ";
        Map<String, Object> config = q.getConfig() != null ? q.getConfig() : Collections.emptyMap();

        switch (type) {
            case "TRUE_FALSE" -> {
                boolean correctValue = false;
                if (config.containsKey("correctValue") && config.get("correctValue") instanceof Boolean b) {
                    correctValue = b;
                } else if (config.containsKey("answer")) {
                    correctValue = Boolean.parseBoolean(String.valueOf(config.get("answer")));
                }

                boolean studentValue = false;
                if (response instanceof Boolean b) {
                    studentValue = b;
                } else if (response instanceof Map<?, ?> m) {
                    Object val = m.get("value") != null ? m.get("value") : m.get("answer");
                    studentValue = val instanceof Boolean ? (Boolean) val : Boolean.parseBoolean(String.valueOf(val));
                } else {
                    studentValue = Boolean.parseBoolean(String.valueOf(response));
                }

                boolean ok = (studentValue == correctValue);
                return new EvaluationSingleResult(ok ? marks : 0, ok, ok ? "Correct" : "Incorrect");
            }

            case "OUTPUT_PREDICTION", "SHORT_ANSWER", "DEBUGGING" -> {
                String expected = "";
                if (config.containsKey("expectedOutput")) {
                    expected = String.valueOf(config.get("expectedOutput"));
                } else if (config.containsKey("evaluationOutput")) {
                    expected = String.valueOf(config.get("evaluationOutput"));
                } else if (config.containsKey("answer")) {
                    expected = String.valueOf(config.get("answer"));
                }

                String actual = "";
                if (response instanceof Map<?, ?> m) {
                    Object val = m.get("output") != null ? m.get("output") : (m.get("answer") != null ? m.get("answer") : m.get("code"));
                    actual = val != null ? String.valueOf(val) : "";
                } else {
                    actual = String.valueOf(response);
                }

                String normExpected = expected.trim().replace("\r\n", "\n");
                String normActual = actual.trim().replace("\r\n", "\n");
                boolean ok = normExpected.equalsIgnoreCase(normActual);
                return new EvaluationSingleResult(ok ? marks : 0, ok, ok ? "Correct" : "Incorrect output");
            }

            case "FILL_IN_THE_BLANKS" -> {
                List<?> blanks = config.get("blanks") instanceof List<?> list ? list : Collections.emptyList();
                List<?> userValues = Collections.emptyList();
                if (response instanceof Map<?, ?> m && m.get("values") instanceof List<?> list) {
                    userValues = list;
                } else if (response instanceof List<?> list) {
                    userValues = list;
                }

                if (blanks.isEmpty() || userValues.isEmpty()) {
                    return new EvaluationSingleResult(0, false, "Blank answers missing");
                }

                int correctBlanks = 0;
                for (int b = 0; b < blanks.size(); b++) {
                    if (b < userValues.size()) {
                        String userVal = String.valueOf(userValues.get(b)).trim().toLowerCase();
                        Object blankObj = blanks.get(b);
                        if (blankObj instanceof Map<?, ?> bMap && bMap.get("correctValues") instanceof List<?> correctList) {
                            boolean matched = correctList.stream()
                                    .anyMatch(cv -> String.valueOf(cv).trim().equalsIgnoreCase(userVal));
                            if (matched) correctBlanks++;
                        }
                    }
                }

                boolean allCorrect = correctBlanks == blanks.size();
                int score = (int) Math.round(((double) correctBlanks / blanks.size()) * marks);
                return new EvaluationSingleResult(score, allCorrect, correctBlanks + "/" + blanks.size() + " blanks correct");
            }

            case "MSQ" -> {
                // Multiple select
                Set<String> correctIds = extractCorrectOptionIds(q, config);
                Set<String> selectedIds = extractSelectedOptionIds(response);

                boolean ok = !correctIds.isEmpty() && correctIds.equals(selectedIds);
                return new EvaluationSingleResult(ok ? marks : 0, ok, ok ? "Correct" : "Incorrect selection");
            }

            default -> {
                // Standard MCQ / Single Choice
                Integer correctIdx = q.getCorrectOption();
                if (correctIdx == null && config.containsKey("correctOption")) {
                    try { correctIdx = Integer.parseInt(String.valueOf(config.get("correctOption"))); } catch (Exception ignored) {}
                }

                Set<String> correctIds = extractCorrectOptionIds(q, config);

                // Check by integer index
                Integer studentIdx = null;
                if (response instanceof Number num) {
                    studentIdx = num.intValue();
                } else if (response instanceof Map<?, ?> m && m.containsKey("selectedIndex")) {
                    try { studentIdx = Integer.parseInt(String.valueOf(m.get("selectedIndex"))); } catch (Exception ignored) {}
                }

                if (studentIdx != null && correctIdx != null) {
                    boolean ok = studentIdx.equals(correctIdx);
                    return new EvaluationSingleResult(ok ? marks : 0, ok, ok ? "Correct" : "Incorrect option");
                }

                // Check by Option ID or Option text
                Set<String> selectedIds = extractSelectedOptionIds(response);
                if (!correctIds.isEmpty() && !selectedIds.isEmpty()) {
                    boolean ok = selectedIds.stream().anyMatch(correctIds::contains);
                    return new EvaluationSingleResult(ok ? marks : 0, ok, ok ? "Correct" : "Incorrect option");
                }

                return new EvaluationSingleResult(0, false, "Option not matched");
            }
        }
    }

    private Set<String> extractCorrectOptionIds(Quiz.QuizQuestionItem q, Map<String, Object> config) {
        Set<String> set = new HashSet<>();
        if (config.get("correctOptionIds") instanceof List<?> list) {
            list.forEach(item -> set.add(String.valueOf(item).trim()));
        }
        if (q.getOptions() != null) {
            for (int i = 0; i < q.getOptions().size(); i++) {
                Object opt = q.getOptions().get(i);
                if (opt instanceof Map<?, ?> m) {
                    if (Boolean.TRUE.equals(m.get("isCorrect"))) {
                        if (m.containsKey("id")) set.add(String.valueOf(m.get("id")).trim());
                    }
                } else if (q.getCorrectOption() != null && q.getCorrectOption() == i) {
                    set.add(String.valueOf(opt).trim());
                }
            }
        }
        return set;
    }

    private Set<String> extractSelectedOptionIds(Object response) {
        Set<String> set = new HashSet<>();
        if (response instanceof Map<?, ?> m) {
            if (m.get("selectedOptionIds") instanceof List<?> list) {
                list.forEach(item -> set.add(String.valueOf(item).trim()));
            } else if (m.containsKey("selectedOptionId")) {
                set.add(String.valueOf(m.get("selectedOptionId")).trim());
            } else if (m.containsKey("selectedOption")) {
                set.add(String.valueOf(m.get("selectedOption")).trim());
            }
        } else if (response instanceof List<?> list) {
            list.forEach(item -> set.add(String.valueOf(item).trim()));
        } else if (response != null) {
            set.add(String.valueOf(response).trim());
        }
        return set;
    }

    private Map<String, Object> parseResponses(Object rawAnswer, Map<String, Object> directAnswers) {
        Map<String, Object> map = new HashMap<>();

        if (directAnswers != null && !directAnswers.isEmpty()) {
            map.putAll(directAnswers);
        }

        if (rawAnswer != null) {
            try {
                String json = rawAnswer instanceof String s ? s : objectMapper.writeValueAsString(rawAnswer);
                Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<>() {});

                if (parsed.get("questions") instanceof List<?> qList) {
                    for (Object item : qList) {
                        if (item instanceof Map<?, ?> qMap) {
                            String id = String.valueOf(qMap.get("id"));
                            Object resp = qMap.get("response");
                            map.put(id, resp);
                        }
                    }
                } else {
                    // Single question response: { selectedIndex: 0, selectedOption: "..." }
                    map.put("default", parsed);
                }
            } catch (Exception e) {
                log.debug("Raw answer is not a JSON object: {}", rawAnswer);
                map.put("default", rawAnswer);
            }
        }

        return map;
    }
}
