package io.github.onetwo.errorautopilot.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [UnifiedError] 및 [FetchErrorsOptions]에 대한 단위 테스트.
 */
class ErrorModelsTest {

    @Test
    fun `UnifiedError isCritical should return true for CRITICAL and ERROR`() {
        val criticalError = createError(Severity.CRITICAL)
        val errorError = createError(Severity.ERROR)
        val warningError = createError(Severity.WARNING)
        val infoError = createError(Severity.INFO)

        assertTrue(criticalError.isCritical())
        assertTrue(errorError.isCritical())
        assertFalse(warningError.isCritical())
        assertFalse(infoError.isCritical())
    }

    @Test
    fun `UnifiedError hasTraceId should return correct value`() {
        val withTraceId = createError(traceId = "abc123")
        val withoutTraceId = createError(traceId = null)

        assertTrue(withTraceId.hasTraceId())
        assertFalse(withoutTraceId.hasTraceId())
    }

    @Test
    fun `FetchErrorsOptions should use default values`() {
        val options = FetchErrorsOptions()

        assertEquals(FetchErrorsOptions.DEFAULT_SINCE_MINUTES, options.sinceMinutes)
        assertEquals(FetchErrorsOptions.DEFAULT_SEVERITY, options.severity)
        assertEquals(FetchErrorsOptions.DEFAULT_LIMIT, options.limit)
        assertEquals(null, options.service)
        assertEquals(null, options.namespace)
    }

    @Test
    fun `FetchErrorsOptions should accept custom values`() {
        val options = FetchErrorsOptions(
            sinceMinutes = 30,
            severity = listOf(Severity.CRITICAL),
            service = "api-server",
            namespace = "production",
            limit = 50
        )

        assertEquals(30, options.sinceMinutes)
        assertEquals(listOf(Severity.CRITICAL), options.severity)
        assertEquals("api-server", options.service)
        assertEquals("production", options.namespace)
        assertEquals(50, options.limit)
    }

    @Test
    fun `FetchErrorsOptions should throw for invalid sinceMinutes`() {
        assertFailsWith<IllegalArgumentException> {
            FetchErrorsOptions(sinceMinutes = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            FetchErrorsOptions(sinceMinutes = -1)
        }
    }

    @Test
    fun `FetchErrorsOptions should throw for invalid limit`() {
        assertFailsWith<IllegalArgumentException> {
            FetchErrorsOptions(limit = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            FetchErrorsOptions(limit = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            FetchErrorsOptions(limit = FetchErrorsOptions.MAX_LIMIT + 1)
        }
    }

    private fun createError(
        severity: Severity = Severity.ERROR,
        traceId: String? = null
    ) = UnifiedError(
        timestamp = Instant.now(),
        severity = severity,
        title = "Test Error",
        message = "Test error message",
        traceId = traceId
    )
}
