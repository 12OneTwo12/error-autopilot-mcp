package io.github.onetwo.errorautopilot.formatter

import io.github.onetwo.errorautopilot.model.Severity
import io.github.onetwo.errorautopilot.model.SpanData
import io.github.onetwo.errorautopilot.model.TraceData
import io.github.onetwo.errorautopilot.model.UnifiedError
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [ErrorFormatter] 및 [TraceFormatter]에 대한 단위 테스트.
 */
class FormattersTest {

    // ==================== ErrorFormatter Tests ====================

    @Test
    fun `ErrorFormatter format should include all errors`() {
        val errors = listOf(
            createError(title = "Error 1"),
            createError(title = "Error 2"),
            createError(title = "Error 3")
        )

        val formatted = ErrorFormatter.format(errors)

        assertTrue(formatted.contains("Error 1"))
        assertTrue(formatted.contains("Error 2"))
        assertTrue(formatted.contains("Error 3"))
        assertTrue(formatted.contains("1."))
        assertTrue(formatted.contains("2."))
        assertTrue(formatted.contains("3."))
    }

    @Test
    fun `ErrorFormatter formatSingle should include severity emoji`() {
        val criticalError = createError(severity = Severity.CRITICAL)
        val errorError = createError(severity = Severity.ERROR)
        val warningError = createError(severity = Severity.WARNING)
        val infoError = createError(severity = Severity.INFO)

        assertTrue(ErrorFormatter.formatSingle(criticalError, 0).contains("🔴"))
        assertTrue(ErrorFormatter.formatSingle(errorError, 0).contains("🟠"))
        assertTrue(ErrorFormatter.formatSingle(warningError, 0).contains("🟡"))
        assertTrue(ErrorFormatter.formatSingle(infoError, 0).contains("🔵"))
    }

    @Test
    fun `ErrorFormatter formatSingle should include optional fields when present`() {
        val error = createError(
            service = "api-server",
            namespace = "production",
            pod = "api-server-abc123",
            traceId = "trace-123"
        )

        val formatted = ErrorFormatter.formatSingle(error, 0)

        assertTrue(formatted.contains("서비스: api-server"))
        assertTrue(formatted.contains("네임스페이스: production"))
        assertTrue(formatted.contains("Pod: api-server-abc123"))
        assertTrue(formatted.contains("Trace ID: trace-123"))
    }

    @Test
    fun `ErrorFormatter formatSingle should truncate long messages`() {
        val longMessage = "a".repeat(300)
        val error = createError(message = longMessage)

        val formatted = ErrorFormatter.formatSingle(error, 0)

        assertTrue(formatted.contains("..."))
        assertTrue(formatted.length < longMessage.length + 200)
    }

    @Test
    fun `ErrorFormatter generateSummary should count by severity`() {
        val errors = listOf(
            createError(severity = Severity.CRITICAL),
            createError(severity = Severity.ERROR),
            createError(severity = Severity.ERROR),
            createError(severity = Severity.WARNING)
        )

        val summary = ErrorFormatter.generateSummary(errors)

        assertTrue(summary.contains("총 4개"))
        assertTrue(summary.contains("CRITICAL: 1개"))
        assertTrue(summary.contains("ERROR: 2개"))
        assertTrue(summary.contains("WARNING: 1개"))
    }

    @Test
    fun `ErrorFormatter generateSummary should count by service`() {
        val errors = listOf(
            createError(service = "service-a"),
            createError(service = "service-a"),
            createError(service = "service-b")
        )

        val summary = ErrorFormatter.generateSummary(errors)

        assertTrue(summary.contains("service-a: 2개"))
        assertTrue(summary.contains("service-b: 1개"))
    }

    // ==================== TraceFormatter Tests ====================

    @Test
    fun `TraceFormatter format should include trace metadata`() {
        val trace = createTrace(
            traceId = "abc123",
            rootService = "gateway",
            rootOperation = "GET /api/users"
        )

        val formatted = TraceFormatter.format(trace)

        assertTrue(formatted.contains("abc123"))
        assertTrue(formatted.contains("gateway"))
        assertTrue(formatted.contains("GET /api/users"))
    }

    @Test
    fun `TraceFormatter format should include spans when present`() {
        val trace = createTrace(
            spans = listOf(
                createSpan(serviceName = "service-a", operationName = "operation-1"),
                createSpan(serviceName = "service-b", operationName = "operation-2")
            )
        )

        val formatted = TraceFormatter.format(trace)

        assertTrue(formatted.contains("service-a"))
        assertTrue(formatted.contains("operation-1"))
        assertTrue(formatted.contains("service-b"))
        assertTrue(formatted.contains("operation-2"))
    }

    @Test
    fun `TraceFormatter format should show error emoji for error spans`() {
        val trace = createTrace(
            spans = listOf(
                createSpan(status = SpanData.STATUS_OK),
                createSpan(status = SpanData.STATUS_ERROR)
            )
        )

        val formatted = TraceFormatter.format(trace)

        assertTrue(formatted.contains("✅"))
        assertTrue(formatted.contains("❌"))
    }

    @Test
    fun `TraceFormatter formatList should show empty message for empty list`() {
        val formatted = TraceFormatter.formatList(emptyList())

        assertEquals("검색된 트레이스가 없습니다.", formatted)
    }

    @Test
    fun `TraceFormatter formatList should include count in header`() {
        val traces = listOf(
            createTrace(traceId = "trace-1"),
            createTrace(traceId = "trace-2"),
            createTrace(traceId = "trace-3")
        )

        val formatted = TraceFormatter.formatList(traces)

        assertTrue(formatted.contains("3개"))
    }

    // ==================== Helper Methods ====================

    private fun createError(
        title: String = "Test Error",
        message: String = "Test message",
        severity: Severity = Severity.ERROR,
        service: String? = null,
        namespace: String? = null,
        pod: String? = null,
        traceId: String? = null
    ) = UnifiedError(
        timestamp = Instant.now(),
        severity = severity,
        title = title,
        message = message,
        service = service,
        namespace = namespace,
        pod = pod,
        traceId = traceId
    )

    private fun createTrace(
        traceId: String = "trace-123",
        rootService: String = "test-service",
        rootOperation: String = "test-operation",
        spans: List<SpanData> = emptyList()
    ) = TraceData(
        traceId = traceId,
        rootService = rootService,
        rootOperation = rootOperation,
        startTime = Instant.now(),
        duration = 100.0,
        spanCount = spans.size,
        spans = spans
    )

    private fun createSpan(
        serviceName: String = "test-service",
        operationName: String = "test-operation",
        status: String = SpanData.STATUS_OK
    ) = SpanData(
        spanId = "span-${System.nanoTime()}",
        serviceName = serviceName,
        operationName = operationName,
        duration = 50.0,
        status = status
    )
}
