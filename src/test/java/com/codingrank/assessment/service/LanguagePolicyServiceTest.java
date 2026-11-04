package com.codingrank.assessment.service;

import com.codingrank.assessment.dto.ExecuteCodeRequest;
import com.codingrank.assessment.dto.SubmitCodeRequest;
import com.codingrank.assessment.model.CurriculumItem;
import com.codingrank.assessment.repository.CurriculumItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class LanguagePolicyServiceTest {

    @Mock
    private CurriculumItemRepository curriculumItemRepository;

    @InjectMocks
    private LanguagePolicyService service;

    private void itemAllows(String... languages) {
        when(curriculumItemRepository.findById("item")).thenReturn(Optional.of(
                CurriculumItem.builder().id("item").allowedLanguages(List.of(languages)).build()));
    }

    @Test
    void rejectsLanguageTheTestDoesNotAllow() {
        itemAllows("javascript");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.assertAllowed("item", "python"));
        assertEquals("This test only allows JavaScript.", error.getMessage());
    }

    @Test
    void rejectsMissingLanguageOnALimitedTest() {
        itemAllows("cpp");
        assertThrows(IllegalArgumentException.class, () -> service.assertAllowed("item", null));
    }

    @Test
    void allowsChosenLanguageInAnyCase() {
        itemAllows("JAVA");
        assertDoesNotThrow(() -> service.assertAllowed("item", "java"));
        assertDoesNotThrow(() -> service.assertAllowed("item", " Java "));
    }

    @Test
    void allowsEveryLanguageWhenThereIsNoLimit() {
        itemAllows();
        assertDoesNotThrow(() -> service.assertAllowed("item", "c"));
        itemAllows("ALL");
        assertDoesNotThrow(() -> service.assertAllowed("item", "c"));
    }

    @Test
    void allowsItemsSavedBeforeTheFieldExisted() {
        CurriculumItem older = CurriculumItem.builder().id("item").allowedLanguages(null).build();
        when(curriculumItemRepository.findById("item")).thenReturn(Optional.of(older));
        assertDoesNotThrow(() -> service.assertAllowed("item", "python"));
    }

    @Test
    void allowsRunsWithNoItemOrAnUnknownItem() {
        assertDoesNotThrow(() -> service.assertAllowed(null, "python"));
        assertDoesNotThrow(() -> service.assertAllowed(" ", "python"));
        verifyNoInteractions(curriculumItemRepository);

        when(curriculumItemRepository.findById("gone")).thenReturn(Optional.empty());
        assertDoesNotThrow(() -> service.assertAllowed("gone", "python"));
    }

    @Test
    void runIsRefusedBeforeAnyCodeIsJudged() {
        itemAllows("javascript");
        CodeJudgeService judge = mock(CodeJudgeService.class);
        CodingService coding = new CodingService(judge, null, null, service);

        ExecuteCodeRequest request = ExecuteCodeRequest.builder()
                .assessmentId("item").language("python").code("print(1)").build();

        assertThrows(IllegalArgumentException.class, () -> coding.executeCode(request));
        verify(judge, never()).evaluate(any(), any(), any(), any());
    }

    @Test
    void submitIsRefusedBeforeAnyCodeIsJudged() {
        itemAllows("javascript");
        CodeJudgeService judge = mock(CodeJudgeService.class);
        SubmissionService submissions = new SubmissionService(null, null, null, null, null, null, null, null,
                judge, null, null, null, service, null);

        SubmitCodeRequest request = SubmitCodeRequest.builder()
                .assessmentId("item").questionId("q").language("python").code("print(1)").build();

        assertThrows(IllegalArgumentException.class,
                () -> submissions.submitCode("user", null, null, request));
        verify(judge, never()).evaluate(any(), any(), any(), any());
    }
}
