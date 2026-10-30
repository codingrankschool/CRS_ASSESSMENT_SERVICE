package com.codingrank.assessment.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MdcLoggingFilterTest {

    private MdcLoggingFilter filter;

    @Mock
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new MdcLoggingFilter();
    }

    @Test
    void shouldGenerateTraceIdAndCleanMdcAfterRequest() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/assessments");
        MockHttpServletResponse response = new MockHttpServletResponse();

        doAnswer(invocation -> {
            // Verify MDC is populated during request execution
            assertNotNull(MDC.get(MdcLoggingFilter.MDC_TRACE_ID));
            assertEquals("GET", MDC.get(MdcLoggingFilter.MDC_HTTP_METHOD));
            assertEquals("/api/v1/assessments", MDC.get(MdcLoggingFilter.MDC_REQUEST_URI));
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilter(request, response, filterChain);

        // Verify response header has trace ID
        String responseTraceId = response.getHeader(MdcLoggingFilter.TRACE_ID_HEADER);
        assertNotNull(responseTraceId);
        assertFalse(responseTraceId.isBlank());

        // Verify MDC was cleaned up to prevent thread-local memory leak
        assertNull(MDC.get(MdcLoggingFilter.MDC_TRACE_ID));
    }

    @Test
    void shouldPreserveExistingCorrelationIdHeader() throws ServletException, IOException {
        String existingTraceId = "custom-trace-id-12345";
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/submissions/code");
        request.addHeader(MdcLoggingFilter.TRACE_ID_HEADER, existingTraceId);
        request.addHeader("X-User-Id", "usr_999");
        MockHttpServletResponse response = new MockHttpServletResponse();

        doAnswer(invocation -> {
            assertEquals(existingTraceId, MDC.get(MdcLoggingFilter.MDC_TRACE_ID));
            assertEquals("usr_999", MDC.get(MdcLoggingFilter.MDC_USER_ID));
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilter(request, response, filterChain);

        assertEquals(existingTraceId, response.getHeader(MdcLoggingFilter.TRACE_ID_HEADER));
        assertNull(MDC.get(MdcLoggingFilter.MDC_TRACE_ID));
    }
}
