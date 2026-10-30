package com.codingrank.assessment.service;

import com.codingrank.assessment.common.enums.AssessmentType;
import com.codingrank.assessment.dto.AssessmentSummaryDto;
import com.codingrank.assessment.dto.ContestDto;
import com.codingrank.assessment.model.*;
import com.codingrank.assessment.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AssessmentService {

    private final AssessmentRepository assessmentRepository;
    private final QuestionRepository questionRepository;
    private final TestCaseRepository testCaseRepository;
    private final ContestRepository contestRepository;
    private final QuizRepository quizRepository;
    private final AssignmentRepository assignmentRepository;
    private final CurriculumItemRepository curriculumItemRepository;

    public List<ContestDto> getAllContests(String filterStatus) {
        log.info("Fetching contests with filterStatus='{}'", filterStatus);
        Instant now = Instant.now();

        // 1. Fetch all Contest documents from MongoDB Atlas
        List<Contest> contests = contestRepository.findAll();
        // 2. Fetch all CurriculumItem of type CONTEST
        List<CurriculumItem> contestItems = curriculumItemRepository.findByTypeIgnoreCase("CONTEST");
        Map<String, CurriculumItem> itemMap = contestItems.stream()
                .filter(i -> i.getId() != null)
                .collect(Collectors.toMap(CurriculumItem::getId, i -> i, (a, b) -> a));

        Map<String, ContestDto> contestDtoMap = new LinkedHashMap<>();

        for (Contest c : contests) {
            String itemId = c.getItemId();
            CurriculumItem item = itemId != null ? itemMap.get(itemId) : null;

            String title = item != null && item.getTitle() != null ? item.getTitle() : "Contest " + c.getId();
            String description = item != null ? item.getDescription() : null;

            String status = determineStatus(c.getStartTime(), c.getEndTime(), now);

            ContestDto dto = ContestDto.builder()
                    .id(c.getId())
                    .itemId(itemId != null ? itemId : c.getId())
                    .title(title)
                    .description(description)
                    .startTime(c.getStartTime())
                    .endTime(c.getEndTime())
                    .status(status)
                    .proctoring(c.isProctoring())
                    .problemCount(c.getProblems() != null ? c.getProblems().size() : 0)
                    .problems(c.getProblems() != null ? c.getProblems() : Collections.emptyList())
                    .build();

            contestDtoMap.put(c.getId(), dto);
        }

        // Include any CurriculumItem marked CONTEST that didn't have a separate Contest document
        for (CurriculumItem item : contestItems) {
            boolean alreadyMapped = contestDtoMap.values().stream()
                    .anyMatch(dto -> item.getId().equals(dto.getItemId()) || item.getId().equals(dto.getId()));

            if (!alreadyMapped) {
                ContestDto dto = ContestDto.builder()
                        .id(item.getId())
                        .itemId(item.getId())
                        .title(item.getTitle())
                        .description(item.getDescription())
                        .startTime(item.getCreatedAt() != null ? item.getCreatedAt() : now)
                        .status("ACTIVE")
                        .proctoring(true)
                        .problemCount(0)
                        .problems(Collections.emptyList())
                        .build();
                contestDtoMap.put(item.getId(), dto);
            }
        }

        // Include any from Assessment repository
        for (Assessment a : assessmentRepository.findAll()) {
            if (a.getType() == AssessmentType.CONTEST && !contestDtoMap.containsKey(a.getId())) {
                String status = determineStatus(a.getStartTime(), a.getEndTime(), now);
                ContestDto dto = ContestDto.builder()
                        .id(a.getId())
                        .itemId(a.getItemId() != null ? a.getItemId() : a.getId())
                        .title(a.getTitle())
                        .description(a.getDescription())
                        .startTime(a.getStartTime())
                        .endTime(a.getEndTime())
                        .status(status)
                        .proctoring(a.isProctored())
                        .problemCount(a.getCodingQuestions() != null ? a.getCodingQuestions().size() : 0)
                        .build();
                contestDtoMap.put(a.getId(), dto);
            }
        }

        List<ContestDto> results = new ArrayList<>(contestDtoMap.values());

        if (filterStatus != null && !filterStatus.isBlank()) {
            results = results.stream()
                    .filter(c -> filterStatus.equalsIgnoreCase(c.getStatus()))
                    .collect(Collectors.toList());
        }

        log.info("Found {} contest(s) in MongoDB Atlas", results.size());
        return results;
    }

    public Optional<ContestDto> getContestDetails(String idOrItemId) {
        log.info("Fetching contest details for idOrItemId='{}'", idOrItemId);
        Instant now = Instant.now();

        // 1. Check Contest repository by id or itemId
        Optional<Contest> contestOpt = contestRepository.findById(idOrItemId);
        if (contestOpt.isEmpty()) {
            contestOpt = contestRepository.findByItemId(idOrItemId);
        }

        if (contestOpt.isPresent()) {
            Contest c = contestOpt.get();
            String itemId = c.getItemId() != null ? c.getItemId() : c.getId();
            CurriculumItem item = curriculumItemRepository.findById(itemId).orElse(null);

            String title = item != null && item.getTitle() != null ? item.getTitle() : "Contest " + c.getId();
            String description = item != null ? item.getDescription() : null;
            String status = determineStatus(c.getStartTime(), c.getEndTime(), now);

            List<Object> rawProblems = c.getProblems() != null ? c.getProblems() : Collections.emptyList();
            List<Object> hydratedProblems = hydrateContestProblems(rawProblems);

            ContestDto dto = ContestDto.builder()
                    .id(c.getId())
                    .itemId(itemId)
                    .title(title)
                    .description(description)
                    .startTime(c.getStartTime())
                    .endTime(c.getEndTime())
                    .status(status)
                    .proctoring(c.isProctoring())
                    .problemCount(hydratedProblems.size())
                    .problems(hydratedProblems)
                    .build();

            return Optional.of(dto);
        }

        // 2. Check CurriculumItem directly
        Optional<CurriculumItem> itemOpt = curriculumItemRepository.findById(idOrItemId);
        if (itemOpt.isPresent() && "CONTEST".equalsIgnoreCase(itemOpt.get().getType())) {
            CurriculumItem item = itemOpt.get();
            ContestDto dto = ContestDto.builder()
                    .id(item.getId())
                    .itemId(item.getId())
                    .title(item.getTitle())
                    .description(item.getDescription())
                    .startTime(item.getCreatedAt() != null ? item.getCreatedAt() : now)
                    .status("ACTIVE")
                    .proctoring(true)
                    .problemCount(0)
                    .problems(Collections.emptyList())
                    .build();
            return Optional.of(dto);
        }

        // 3. Check Assessment collection
        Optional<Assessment> assessmentOpt = assessmentRepository.findById(idOrItemId);
        if (assessmentOpt.isEmpty()) {
            assessmentOpt = assessmentRepository.findByItemId(idOrItemId);
        }
        if (assessmentOpt.isPresent()) {
            Assessment a = assessmentOpt.get();
            String status = determineStatus(a.getStartTime(), a.getEndTime(), now);
            ContestDto dto = ContestDto.builder()
                    .id(a.getId())
                    .itemId(a.getItemId() != null ? a.getItemId() : a.getId())
                    .title(a.getTitle())
                    .description(a.getDescription())
                    .startTime(a.getStartTime())
                    .endTime(a.getEndTime())
                    .status(status)
                    .proctoring(a.isProctored())
                    .problemCount(a.getCodingQuestions() != null ? a.getCodingQuestions().size() : 0)
                    .build();
            return Optional.of(dto);
        }

        return Optional.empty();
    }

    public List<AssessmentSummaryDto> getAllAssessments(String typeFilter) {
        log.info("Fetching unified assessments with typeFilter='{}'", typeFilter);
        Instant now = Instant.now();
        List<AssessmentSummaryDto> list = new ArrayList<>();

        // Fetch all Contests
        if (typeFilter == null || "CONTEST".equalsIgnoreCase(typeFilter)) {
            for (ContestDto c : getAllContests(null)) {
                list.add(AssessmentSummaryDto.builder()
                        .id(c.getId())
                        .itemId(c.getItemId())
                        .title(c.getTitle())
                        .description(c.getDescription())
                        .type("CONTEST")
                        .status(c.getStatus())
                        .startTime(c.getStartTime())
                        .endTime(c.getEndTime())
                        .proctored(c.isProctoring())
                        .build());
            }
        }

        // Fetch Quizzes from Atlas
        if (typeFilter == null || "QUIZ".equalsIgnoreCase(typeFilter)) {
            List<Quiz> quizzes = quizRepository.findAll();
            List<CurriculumItem> quizItems = curriculumItemRepository.findByTypeIgnoreCase("QUIZ");
            Map<String, CurriculumItem> quizItemMap = quizItems.stream()
                    .collect(Collectors.toMap(CurriculumItem::getId, i -> i, (a, b) -> a));

            for (Quiz q : quizzes) {
                CurriculumItem item = q.getItemId() != null ? quizItemMap.get(q.getItemId()) : null;
                String title = item != null && item.getTitle() != null ? item.getTitle() : "Quiz " + q.getId();
                String status = determineStatus(q.getStartTime(), q.getEndTime(), now);

                list.add(AssessmentSummaryDto.builder()
                        .id(q.getId())
                        .itemId(q.getItemId())
                        .title(title)
                        .description(item != null ? item.getDescription() : null)
                        .type("QUIZ")
                        .status(status)
                        .startTime(q.getStartTime())
                        .endTime(q.getEndTime())
                        .durationMinutes(q.getTimeLimit())
                        .build());
            }
        }

        // Fetch Assignments from Atlas
        if (typeFilter == null || "ASSIGNMENT".equalsIgnoreCase(typeFilter)) {
            List<Assignment> assignments = assignmentRepository.findAll();
            List<CurriculumItem> assignmentItems = curriculumItemRepository.findByTypeIgnoreCase("ASSIGNMENT");
            Map<String, CurriculumItem> assignmentItemMap = assignmentItems.stream()
                    .collect(Collectors.toMap(CurriculumItem::getId, i -> i, (a, b) -> a));

            for (Assignment a : assignments) {
                CurriculumItem item = a.getItemId() != null ? assignmentItemMap.get(a.getItemId()) : null;
                String title = item != null && item.getTitle() != null ? item.getTitle() : "Assignment " + a.getId();
                String status = determineStatus(a.getStartTime(), a.getEndTime(), now);

                list.add(AssessmentSummaryDto.builder()
                        .id(a.getId())
                        .itemId(a.getItemId())
                        .title(title)
                        .description(item != null ? item.getDescription() : a.getProblemStatement())
                        .type("ASSIGNMENT")
                        .status(status)
                        .startTime(a.getStartTime())
                        .endTime(a.getEndTime())
                        .durationMinutes(a.getDuration())
                        .maxScore(a.getTotalMarks())
                        .build());
            }
        }

        return list;
    }

    private List<Object> hydrateContestProblems(List<Object> rawProblems) {
        List<Object> hydrated = new ArrayList<>();
        for (Object p : rawProblems) {
            String questionId = null;
            if (p instanceof String s) {
                questionId = s;
            } else if (p instanceof Map<?, ?> m) {
                Object qIdObj = m.get("questionId");
                if (qIdObj == null) qIdObj = m.get("id");
                if (qIdObj != null) questionId = qIdObj.toString();
            }

            if (questionId != null) {
                Optional<Question> qOpt = questionRepository.findById(questionId);
                if (qOpt.isPresent()) {
                    Question q = qOpt.get();
                    List<TestCase> testCases = testCaseRepository.findByQuestionIdAndIsHiddenFalse(q.getId());

                    Map<String, Object> qMap = new LinkedHashMap<>();
                    qMap.put("id", q.getId());
                    qMap.put("title", q.getTitle());
                    qMap.put("description", q.getDescription());
                    qMap.put("difficulty", q.getDifficulty());
                    qMap.put("type", q.getType());
                    qMap.put("points", q.getPoints());
                    qMap.put("timeLimitMs", q.getTimeLimitMs());
                    qMap.put("memoryLimitMb", q.getMemoryLimitMb());
                    qMap.put("constraints", q.getConstraints());
                    qMap.put("starterCodeTemplate", q.getStarterCodeTemplate());
                    qMap.put("sampleTestCases", testCases);

                    if (p instanceof Map<?, ?> m) {
                        if (m.get("marks") != null) qMap.put("points", m.get("marks"));
                        if (m.get("order") != null) qMap.put("order", m.get("order"));
                    }
                    hydrated.add(qMap);
                    continue;
                }
            }
            hydrated.add(p);
        }
        return hydrated;
    }

    private String determineStatus(Instant start, Instant end, Instant now) {
        if (start != null && now.isBefore(start)) {
            return "UPCOMING";
        }
        if (end != null && now.isAfter(end)) {
            return "COMPLETED";
        }
        return "ACTIVE";
    }

    // Helper methods retained for backward compatibility
    public Optional<Assessment> getAssessmentById(String id) {
        return assessmentRepository.findById(id);
    }

    public Optional<Assessment> getAssessmentByRefId(String refId) {
        return assessmentRepository.findByRefId(refId);
    }

    public Assessment createAssessment(Assessment assessment) {
        return assessmentRepository.save(assessment);
    }

    public Optional<Question> getQuestionById(String questionId) {
        return questionRepository.findById(questionId);
    }

    public List<TestCase> getSanitizedTestCases(String questionId) {
        return testCaseRepository.findByQuestionIdAndIsHiddenFalse(questionId);
    }

    public Optional<Quiz> getQuizByItemId(String itemId) {
        return quizRepository.findByItemId(itemId);
    }

    public Optional<Contest> getContestByItemId(String itemId) {
        return contestRepository.findByItemId(itemId);
    }

    public Optional<Assignment> getAssignmentByItemId(String itemId) {
        return assignmentRepository.findByItemId(itemId);
    }
}
