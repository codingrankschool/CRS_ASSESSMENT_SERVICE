package com.codingrank.assessment.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JudgeProgressRegistryTest {

    @Test
    void countsFinishedCasesForTheStudentWhoSubmitted() {
        JudgeProgressRegistry registry = new JudgeProgressRegistry();
        registry.start("abc12345", "student-1", 20);
        registry.caseDone("abc12345");
        registry.caseDone("abc12345");

        JudgeProgressRegistry.Progress progress = registry.read("abc12345", "student-1").orElseThrow();
        assertEquals(2, progress.done());
        assertEquals(20, progress.total());
        assertEquals("running", progress.phase());

        // Someone else cannot read it.
        assertTrue(registry.read("abc12345", "student-2").isEmpty());

        registry.finish("abc12345");
        assertEquals(20, registry.read("abc12345", "student-1").orElseThrow().done());
        assertEquals("done", registry.read("abc12345", "student-1").orElseThrow().phase());
    }

    @Test
    void ignoresMissingOrOddIds() {
        JudgeProgressRegistry registry = new JudgeProgressRegistry();
        registry.start(null, "student-1", 5);
        registry.start("../etc", "student-1", 5);
        registry.caseDone(null);
        assertFalse(JudgeProgressRegistry.isValidId("short"));
        assertTrue(registry.read("../etc", "student-1").isEmpty());
    }
}
