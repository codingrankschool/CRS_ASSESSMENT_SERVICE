package com.codingrank.assessment.service;

import com.codingrank.assessment.common.enums.AssessmentType;
import com.codingrank.assessment.common.enums.SubmissionStatus;
import com.codingrank.assessment.dto.ExecutionResultDto;
import com.codingrank.assessment.dto.SubmitCodeRequest;
import com.codingrank.assessment.dto.SubmitQuizRequest;
import com.codingrank.assessment.model.*;
import com.codingrank.assessment.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubmissionService {

    private final UserRepository userRepository;
    private final AssessmentRepository assessmentRepository;
    private final ContestRepository contestRepository;
    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;
    private final TestCaseRepository testCaseRepository;
    private final SubmissionRepository submissionRepository;
    private final CheatEventRepository cheatEventRepository;
    private final CodeJudgeService codeJudgeService;
    private final LeaderboardService leaderboardService;
    private final LmsIntegrationService lmsIntegrationService;
    private final QuizEvaluatorService quizEvaluatorService;
    private final LanguagePolicyService languagePolicyService;
    private final MongoTemplate mongoTemplate;

    public Submission submitCode(String userId, String userEmail, String userName, SubmitCodeRequest request) {
        languagePolicyService.assertAllowed(request.getAssessmentId(), request.getLanguage());

        // Resolve real student name and email from Atlas User collection if available
        if (userId != null && !userId.isBlank()) {
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isPresent()) {
                User u = userOpt.get();
                if (u.getEmail() != null) userEmail = u.getEmail();
                if (u.getName() != null) userName = u.getName();
            }
        }
        if (userEmail == null || userEmail.isBlank()) userEmail = "student@codingrankschool.com";
        if (userName == null || userName.isBlank()) userName = "Student";

        Question question = questionRepository.findById(request.getQuestionId())
                .orElseThrow(() -> new IllegalArgumentException("Question not found with ID: " + request.getQuestionId()));

        // Resolve test cases from (1) Question document, (2) TestCase collection, or (3) Contest problem definition
        List<TestCase> testCases = resolveTestCases(question, request.getAssessmentId());

        log.info("Evaluating code submission: userId='{}' user='{}' assessmentId='{}' questionId='{}' testCasesCount={}",
                userId, userName, request.getAssessmentId(), request.getQuestionId(), testCases.size());

        boolean isDisqualified = request.isProctoredDisqualified() || request.getWarningsCount() > 3;

        if (isDisqualified) {
            log.warn("Anti-cheat disqualification flagged for userId='{}' on itemId='{}'.", userId, request.getAssessmentId());
            recordCheatEvent(userId, request.getAssessmentId(), request.getQuestionId(), null,
                    "EXCESSIVE_WARNINGS", Map.of("warningsCount", request.getWarningsCount()));
        }

        // Run evaluation through sandboxed judge
        ExecutionResultDto evalResult = isDisqualified
                ? ExecutionResultDto.builder()
                    .status(SubmissionStatus.DISQUALIFIED)
                    .score(0)
                    .compilerOutput("Disqualified due to anti-cheat violation")
                    .build()
                : codeJudgeService.evaluate(question, testCases, request.getCode(), request.getLanguage());

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("warningsCount", request.getWarningsCount());
        metadata.put("disqualified", isDisqualified);
        if (evalResult.getTestCaseDetails() != null) {
            metadata.put("testCaseDetails", evalResult.getTestCaseDetails());
        }
        if (request.getBatchId() != null) metadata.put("batchId", request.getBatchId());
        if (request.getCourseId() != null) metadata.put("courseId", request.getCourseId());

        Submission submission = Submission.builder()
                .userId(userId)
                .userEmail(userEmail)
                .userName(userName)
                .itemId(request.getAssessmentId())
                .questionId(request.getQuestionId())
                .code(request.getCode())
                .language(request.getLanguage())
                .status(evalResult.getStatus().name())
                .score(evalResult.getScore())
                .testCasesPassed(evalResult.getTestCasesPassed())
                .testCasesTotal(evalResult.getTestCasesTotal())
                .runtimeMs(evalResult.getRuntimeMs())
                .memoryKb(evalResult.getMemoryKb())
                .compilerOutput(evalResult.getCompilerOutput())
                .metadata(metadata)
                .createdAt(Instant.now())
                .build();

        Submission saved = submissionRepository.save(submission);

        log.info("Saved code submission to MongoDB Atlas: submissionId='{}' status={} score={}/{}",
                saved.getId(), saved.getStatus(), saved.getScore(), question.getPoints());

        // Update real-time contest leaderboard asynchronously
        final String assessmentId = request.getAssessmentId();
        CompletableFuture.runAsync(() -> {
            try {
                leaderboardService.recalculateLeaderboard(assessmentId);
            } catch (Exception e) {
                log.debug("No live contest leaderboard active for itemId='{}': {}", assessmentId, e.getMessage());
            }
        });

        // Notify LMS of assessment score
        lmsIntegrationService.notifyLmsAssessmentCompleted(
                userId,
                request.getBatchId(),
                request.getCourseId(),
                request.getAssessmentId(),
                saved.getStatus(),
                saved.getScore(),
                question.getPoints(),
                isDisqualified
        );

        return saved;
    }

    public Submission submitQuiz(String userId, String userEmail, String userName, SubmitQuizRequest request) {
        if (userId != null && !userId.isBlank()) {
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isPresent()) {
                User u = userOpt.get();
                if (u.getEmail() != null) userEmail = u.getEmail();
                if (u.getName() != null) userName = u.getName();
            }
        }
        if (userEmail == null || userEmail.isBlank()) userEmail = "student@codingrankschool.com";
        if (userName == null || userName.isBlank()) userName = "Student";

        String effectiveId = request.getEffectiveAssessmentId();
        log.info("Received quiz submission: userId='{}' assessmentId='{}'", userId, effectiveId);

        Optional<Quiz> quizOpt = quizRepository.findByItemId(effectiveId);
        if (quizOpt.isEmpty()) {
            quizOpt = quizRepository.findById(effectiveId);
        }

        List<Quiz.QuizQuestionItem> questionItems = new ArrayList<>();
        if (quizOpt.isPresent()) {
            questionItems = quizOpt.get().getQuestions();
        } else {
            Optional<Assessment> assessmentOpt = assessmentRepository.findById(effectiveId);
            if (assessmentOpt.isPresent()) {
                Assessment assessment = assessmentOpt.get();
                for (QuizQuestion qq : assessment.getQuizQuestions()) {
                    questionItems.add(Quiz.QuizQuestionItem.builder()
                            .id(qq.getId())
                            .title(qq.getTitle())
                            .question(qq.getQuestionText())
                            .type(qq.getType())
                            .marks(qq.getMarks())
                            .points(qq.getPoints())
                            .options(qq.getOptions())
                            .correctOption(qq.getCorrectOptionIndex())
                            .config(qq.getConfig())
                            .build());
                }
            }
        }

        QuizEvaluatorService.QuizEvaluationResult evalResult = quizEvaluatorService.evaluate(
                questionItems,
                request.getAnswer(),
                request.getAnswers()
        );

        int totalScore = evalResult.getTotalScore();
        int maxScore = evalResult.getMaxScore();
        int correctCount = evalResult.getCorrectCount();
        int totalQuestions = evalResult.getTotalQuestions();

        boolean isDisqualified = request.isProctoredDisqualified() || request.getWarningsCount() > 3;
        String status = isDisqualified ? "DISQUALIFIED" : (correctCount == totalQuestions && totalQuestions > 0 ? "PASSED" : (totalScore > 0 ? "PARTIAL" : "FAILED"));

        Map<String, Object> metadata = new HashMap<>();
        if (request.getMetadata() != null) metadata.putAll(request.getMetadata());
        metadata.put("warningsCount", request.getWarningsCount());
        metadata.put("disqualified", isDisqualified);
        metadata.put("correctCount", correctCount);
        metadata.put("totalQuestions", totalQuestions);

        Map<String, Object> quizMeta = new HashMap<>();
        quizMeta.put("questionResults", evalResult.getQuestionResults());
        metadata.put("quiz", quizMeta);
        metadata.put("questionResults", evalResult.getQuestionResults());

        String rawAnswerStr = request.getAnswer() != null
                ? request.getAnswer().toString()
                : (request.getAnswers() != null ? request.getAnswers().toString() : "{}");

        Submission submission = Submission.builder()
                .userId(userId)
                .userEmail(userEmail)
                .userName(userName)
                .itemId(effectiveId)
                .questionId(request.getQuestionId())
                .answer(rawAnswerStr)
                .status(status)
                .score(isDisqualified ? 0 : totalScore)
                .testCasesPassed(correctCount)
                .testCasesTotal(totalQuestions)
                .metadata(metadata)
                .compilerOutput("Quiz evaluated. Score: " + totalScore + "/" + maxScore + " (" + correctCount + "/" + totalQuestions + " correct)")
                .createdAt(Instant.now())
                .build();

        Submission saved = submissionRepository.save(submission);

        log.info("Saved quiz submission to MongoDB Atlas: submissionId='{}' status={} score={}/{}",
                saved.getId(), saved.getStatus(), saved.getScore(), maxScore);

        // Update contest leaderboard asynchronously if this is a live contest
        final String contestItemId = effectiveId;
        CompletableFuture.runAsync(() -> {
            try {
                leaderboardService.recalculateLeaderboard(contestItemId);
            } catch (Exception e) {
                log.debug("Leaderboard recalculation skipped: {}", e.getMessage());
            }
        });

        lmsIntegrationService.notifyLmsAssessmentCompleted(
                userId,
                request.getBatchId(),
                request.getCourseId(),
                effectiveId,
                saved.getStatus(),
                saved.getScore(),
                maxScore,
                isDisqualified
        );

        return saved;
    }

    public CheatEvent recordCheatEvent(String userId, String itemId, String questionId,
                                       String submissionId, String eventType, Map<String, Object> details) {
        log.warn("Proctoring incident flagged: userId='{}' itemId='{}' event='{}'", userId, itemId, eventType);

        CheatEvent event = CheatEvent.builder()
                .userId(userId)
                .itemId(itemId)
                .questionId(questionId)
                .submissionId(submissionId)
                .eventType(eventType)
                .details(details != null ? details : new HashMap<>())
                .source("assessment-engine")
                .createdAt(Instant.now())
                .build();

        return cheatEventRepository.save(event);
    }

    public List<TestCase> resolveTestCases(Question question, String assessmentId) {
        // 1. Check embedded in Question
        if (question.getTestCases() != null && !question.getTestCases().isEmpty()) {
            return question.getTestCases();
        }

        // 2. Query TestCase collection by String or BSON ObjectId
        try {
            Criteria criteria;
            if (ObjectId.isValid(question.getId())) {
                criteria = new Criteria().orOperator(
                        Criteria.where("questionId").is(question.getId()),
                        Criteria.where("questionId").is(new ObjectId(question.getId()))
                );
            } else {
                criteria = Criteria.where("questionId").is(question.getId());
            }
            List<TestCase> dbTestCases = mongoTemplate.find(new Query(criteria), TestCase.class);
            if (!dbTestCases.isEmpty()) {
                return dbTestCases;
            }
        } catch (Exception e) {
            log.warn("Failed to query TestCase collection with MongoTemplate: {}", e.getMessage());
        }

        // 3. Check if test cases are embedded in Contest problem configuration
        if (assessmentId != null && !assessmentId.isBlank()) {
            Optional<Contest> contestOpt = contestRepository.findById(assessmentId);
            if (contestOpt.isEmpty()) {
                contestOpt = contestRepository.findByItemId(assessmentId);
            }
            if (contestOpt.isPresent()) {
                Contest c = contestOpt.get();
                for (Object probObj : c.getProblems()) {
                    if (probObj instanceof Map<?, ?> map) {
                        String pQId = Objects.toString(map.get("questionId"), Objects.toString(map.get("id"), null));
                        if (question.getId().equals(pQId)) {
                            Object tcObj = map.get("testCases");
                            if (tcObj instanceof List<?> list && !list.isEmpty()) {
                                List<TestCase> result = new ArrayList<>();
                                for (Object item : list) {
                                    if (item instanceof Map<?, ?> tcMap) {
                                        result.add(TestCase.builder()
                                                .input(Objects.toString(tcMap.get("input"), ""))
                                                .output(Objects.toString(tcMap.get("output"), ""))
                                                .explanation(Objects.toString(tcMap.get("explanation"), null))
                                                .isHidden(Boolean.TRUE.equals(tcMap.get("isHidden")))
                                                .build());
                                    }
                                }
                                return result;
                            }
                        }
                    }
                }
            }
        }

        return Collections.emptyList();
    }
}
