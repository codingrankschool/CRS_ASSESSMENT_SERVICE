package com.codingrank.assessment.service;

import com.codingrank.assessment.model.CurriculumItem;

import java.util.Set;

/**
 * Decides whether a student may see the data of a hidden test case they failed.
 * Practice work only. Tests, contests and shared test links stay hidden, because otherwise a student
 * could read the answers one submission at a time. Anything unknown counts as a test.
 */
final class HiddenCaseRevealPolicy {

    private static final Set<String> PRACTICE_TYPES = Set.of("CODING", "ASSIGNMENT");

    private HiddenCaseRevealPolicy() {
    }

    static boolean canReveal(CurriculumItem item) {
        if (item == null || item.getType() == null) {
            return false;
        }
        return PRACTICE_TYPES.contains(item.getType().toUpperCase())
                && !"EVALUATION".equalsIgnoreCase(item.getCategory())
                && !"LINK".equalsIgnoreCase(item.getAudience())
                && item.getParentItemId() == null;
    }
}
