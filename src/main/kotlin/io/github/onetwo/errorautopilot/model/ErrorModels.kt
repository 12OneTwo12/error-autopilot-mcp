package io.github.onetwo.errorautopilot.model

import java.time.Instant
import java.util.UUID

data class UnifiedError(
    val id: String = UUID.randomUUID().toString(),
    val source: String = "loki",
    val timestamp: Instant,
    val severity: Severity,
    val title: String,
    val message: String,
    val stackTrace: String? = null,
    val service: String? = null,
    val namespace: String? = null,
    val pod: String? = null,
    val container: String? = null,
    val traceId: String? = null,
    val labels: Map<String, String> = emptyMap(),
    val raw: Any? = null
)

data class FetchErrorsOptions(
    val sinceMinutes: Int = 60,
    val severity: List<Severity> = listOf(Severity.ERROR, Severity.CRITICAL),
    val service: String? = null,
    val namespace: String? = null,
    val limit: Int = 100
)
