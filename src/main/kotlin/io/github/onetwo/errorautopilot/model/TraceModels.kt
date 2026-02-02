package io.github.onetwo.errorautopilot.model

import java.time.Instant

data class TraceData(
    val traceId: String,
    val rootService: String,
    val rootOperation: String,
    val startTime: Instant,
    val duration: Double, // milliseconds
    val spanCount: Int,
    val spans: List<SpanData>
)

data class SpanData(
    val spanId: String,
    val parentSpanId: String? = null,
    val serviceName: String,
    val operationName: String,
    val startTime: Instant? = null,
    val duration: Double, // milliseconds
    val status: String, // "ok" or "error"
    val attributes: Map<String, String> = emptyMap()
)
