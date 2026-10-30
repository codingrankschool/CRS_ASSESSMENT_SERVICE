package com.codingrank.assessment.common.exception;

import com.codingrank.assessment.config.MdcLoggingFilter;
import com.codingrank.assessment.dto.ApiResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        MDC.put(MdcLoggingFilter.MDC_TRACE_ID, "test-trace-abc-123");
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void shouldHandleIllegalArgumentExceptionWithWarnLog() {
        IllegalArgumentException ex = new IllegalArgumentException("Contest not found: cnt_999");

        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleIllegalArgumentException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Contest not found: cnt_999", response.getBody().getMessage());
    }

    @Test
    void shouldHandleGenericExceptionAndReturnTraceId() {
        RuntimeException ex = new RuntimeException("Database connection timeout");

        ResponseEntity<ApiResponse<Map<String, String>>> response = exceptionHandler.handleGenericException(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertTrue(response.getBody().getMessage().contains("test-trace-abc-123"));
        assertEquals("test-trace-abc-123", response.getBody().getData().get("traceId"));
    }
}
