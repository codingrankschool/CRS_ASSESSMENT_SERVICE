package com.codingrank.assessment.common.enums;

public enum SubmissionStatus {
    QUEUED,
    RUNNING,
    PASSED,
    FAILED,
    TLE,           // Time Limit Exceeded
    MLE,           // Memory Limit Exceeded
    CE,            // Compilation Error
    RTE,           // Runtime Error
    DISQUALIFIED   // Proctored violation
}
