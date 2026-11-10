package com.codingrank.assessment.service;

import com.codingrank.assessment.model.CurriculumItem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HiddenCaseRevealPolicyTest {

    private CurriculumItem item(String type) {
        return CurriculumItem.builder().type(type).build();
    }

    @Test
    void revealsInPractice() {
        assertTrue(HiddenCaseRevealPolicy.canReveal(item("CODING")));
        assertTrue(HiddenCaseRevealPolicy.canReveal(item("assignment")));
    }

    @Test
    void keepsTestsHidden() {
        assertFalse(HiddenCaseRevealPolicy.canReveal(item("CONTEST")));
        assertFalse(HiddenCaseRevealPolicy.canReveal(item("ASSESSMENT")));

        CurriculumItem evaluation = item("CODING");
        evaluation.setCategory("EVALUATION");
        assertFalse(HiddenCaseRevealPolicy.canReveal(evaluation));

        CurriculumItem testLink = item("CODING");
        testLink.setAudience("LINK");
        assertFalse(HiddenCaseRevealPolicy.canReveal(testLink));

        CurriculumItem section = item("CODING");
        section.setParentItemId("parent-test");
        assertFalse(HiddenCaseRevealPolicy.canReveal(section));
    }

    @Test
    void treatsUnknownItemsAsTests() {
        assertFalse(HiddenCaseRevealPolicy.canReveal(null));
        assertFalse(HiddenCaseRevealPolicy.canReveal(item(null)));
    }
}
