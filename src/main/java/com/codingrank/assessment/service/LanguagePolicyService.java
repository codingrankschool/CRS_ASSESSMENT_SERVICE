package com.codingrank.assessment.service;

import com.codingrank.assessment.model.CurriculumItem;
import com.codingrank.assessment.repository.CurriculumItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Applies the language limit an admin set on a test or assignment. The website falls back
 * to the NestJS API when this service refuses, and that API applies the same rule.
 */
@Service
@RequiredArgsConstructor
public class LanguagePolicyService {

    private static final Map<String, String> LABELS = Map.of(
            "javascript", "JavaScript",
            "python", "Python",
            "java", "Java",
            "cpp", "C++",
            "c", "C"
    );

    private final CurriculumItemRepository curriculumItemRepository;

    public void assertAllowed(String itemId, String language) {
        if (itemId == null || itemId.isBlank()) return;

        List<String> allowed = curriculumItemRepository.findById(itemId)
                .map(CurriculumItem::getAllowedLanguages)
                .map(LanguagePolicyService::normalize)
                .orElse(List.of());
        if (allowed.isEmpty()) return;

        String requested = language == null ? "" : language.trim().toLowerCase(Locale.ROOT);
        if (allowed.contains(requested)) return;

        String names = allowed.stream()
                .map(value -> LABELS.getOrDefault(value, value))
                .collect(Collectors.joining(", "));
        throw new IllegalArgumentException("This test only allows " + names + ".");
    }

    // Older items saved uppercase values (JAVA) and some use ALL for no limit.
    private static List<String> normalize(List<String> values) {
        List<String> languages = values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .toList();
        return languages.contains("all") ? List.of() : languages;
    }
}
