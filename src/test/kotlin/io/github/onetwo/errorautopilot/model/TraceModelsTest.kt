package io.github.onetwo.errorautopilot.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [TraceData] 및 [SpanData]에 대한 단위 테스트.
 */
class TraceModelsTest {

    @Test
    fun `TraceData hasError should return true when any span has error`() {
        val traceWithError = createTrace(
            spans = listOf(
                createSpan(status = SpanData.STATUS_OK),
                createSpan(status = SpanData.STATUS_ERROR)
            )
        )
        val traceWithoutError = createTrace(
            spans = listOf(
                createSpan(status = SpanData.STATUS_OK),
                createSpan(status = SpanData.STATUS_OK)
            )
        )

        assertTrue(traceWithError.hasError())
        assertFalse(traceWithoutError.hasError())
    }

    @Test
    fun `TraceData isSlow should check against threshold`() {
        val slowTrace = createTrace(duration = 1500.0)
        val fastTrace = createTrace(duration = 500.0)

        assertTrue(slowTrace.isSlow())
        assertFalse(fastTrace.isSlow())
        assertTrue(fastTrace.isSlow(thresholdMs = 400.0))
    }

    @Test
    fun `TraceData filterByService should return matching spans`() {
        val trace = createTrace(
            spans = listOf(
                createSpan(serviceName = "service-a"),
                createSpan(serviceName = "service-b"),
                createSpan(serviceName = "service-a")
            )
        )

        val filtered = trace.filterByService("service-a")

        assertEquals(2, filtered.size)
        assertTrue(filtered.all { it.serviceName == "service-a" })
    }

    @Test
    fun `SpanData isError should check status`() {
        val errorSpan = createSpan(status = SpanData.STATUS_ERROR)
        val okSpan = createSpan(status = SpanData.STATUS_OK)

        assertTrue(errorSpan.isError())
        assertFalse(okSpan.isError())
    }

    @Test
    fun `SpanData isRoot should check parentSpanId`() {
        val rootSpan = createSpan(parentSpanId = null)
        val childSpan = createSpan(parentSpanId = "parent-123")

        assertTrue(rootSpan.isRoot())
        assertFalse(childSpan.isRoot())
    }

    @Test
    fun `SpanData getHttpAttributes should filter http attributes`() {
        val span = createSpan(
            attributes = mapOf(
                "http.method" to "GET",
                "http.url" to "/api/users",
                "http.status_code" to "200",
                "db.statement" to "SELECT *",
                "custom.key" to "value"
            )
        )

        val httpAttrs = span.getHttpAttributes()

        assertEquals(3, httpAttrs.size)
        assertEquals("GET", httpAttrs["http.method"])
        assertEquals("/api/users", httpAttrs["http.url"])
        assertEquals("200", httpAttrs["http.status_code"])
    }

    @Test
    fun `SpanData getDbAttributes should filter db attributes`() {
        val span = createSpan(
            attributes = mapOf(
                "db.statement" to "SELECT *",
                "db.type" to "postgresql",
                "http.method" to "GET"
            )
        )

        val dbAttrs = span.getDbAttributes()

        assertEquals(2, dbAttrs.size)
        assertEquals("SELECT *", dbAttrs["db.statement"])
        assertEquals("postgresql", dbAttrs["db.type"])
    }

    private fun createTrace(
        duration: Double = 100.0,
        spans: List<SpanData> = emptyList()
    ) = TraceData(
        traceId = "trace-123",
        rootService = "root-service",
        rootOperation = "GET /api",
        startTime = Instant.now(),
        duration = duration,
        spanCount = spans.size,
        spans = spans
    )

    private fun createSpan(
        serviceName: String = "test-service",
        status: String = SpanData.STATUS_OK,
        parentSpanId: String? = "parent-123",
        attributes: Map<String, String> = emptyMap()
    ) = SpanData(
        spanId = "span-${System.nanoTime()}",
        parentSpanId = parentSpanId,
        serviceName = serviceName,
        operationName = "test-operation",
        duration = 50.0,
        status = status,
        attributes = attributes
    )
}
