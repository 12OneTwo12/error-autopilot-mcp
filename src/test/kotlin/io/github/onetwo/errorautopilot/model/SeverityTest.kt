package io.github.onetwo.errorautopilot.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [Severity] 열거형에 대한 단위 테스트.
 */
class SeverityTest {

    @Test
    fun `fromString should convert valid uppercase string`() {
        assertEquals(Severity.CRITICAL, Severity.fromString("CRITICAL"))
        assertEquals(Severity.ERROR, Severity.fromString("ERROR"))
        assertEquals(Severity.WARNING, Severity.fromString("WARNING"))
        assertEquals(Severity.INFO, Severity.fromString("INFO"))
    }

    @Test
    fun `fromString should convert valid lowercase string`() {
        assertEquals(Severity.CRITICAL, Severity.fromString("critical"))
        assertEquals(Severity.ERROR, Severity.fromString("error"))
        assertEquals(Severity.WARNING, Severity.fromString("warning"))
        assertEquals(Severity.INFO, Severity.fromString("info"))
    }

    @Test
    fun `fromString should convert mixed case string`() {
        assertEquals(Severity.CRITICAL, Severity.fromString("Critical"))
        assertEquals(Severity.ERROR, Severity.fromString("Error"))
        assertEquals(Severity.WARNING, Severity.fromString("Warning"))
        assertEquals(Severity.INFO, Severity.fromString("Info"))
    }

    @Test
    fun `fromString should return null for invalid string`() {
        assertNull(Severity.fromString("unknown"))
        assertNull(Severity.fromString("debug"))
        assertNull(Severity.fromString(""))
        assertNull(Severity.fromString("FATAL"))
    }

    @Test
    fun `fromStringOrDefault should return default for invalid string`() {
        assertEquals(Severity.INFO, Severity.fromStringOrDefault("unknown"))
        assertEquals(Severity.ERROR, Severity.fromStringOrDefault("unknown", Severity.ERROR))
    }

    @Test
    fun `fromStringOrDefault should return parsed value for valid string`() {
        assertEquals(Severity.CRITICAL, Severity.fromStringOrDefault("critical"))
        assertEquals(Severity.CRITICAL, Severity.fromStringOrDefault("critical", Severity.INFO))
    }
}
