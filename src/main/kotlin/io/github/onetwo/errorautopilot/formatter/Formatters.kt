package io.github.onetwo.errorautopilot.formatter

import io.github.onetwo.errorautopilot.model.Severity
import io.github.onetwo.errorautopilot.model.TraceData
import io.github.onetwo.errorautopilot.model.UnifiedError

/**
 * 에러 포맷팅 담당
 */
object ErrorFormatter {
    private val SEVERITY_EMOJI = mapOf(
        Severity.CRITICAL to "🔴",
        Severity.ERROR to "🟠",
        Severity.WARNING to "🟡",
        Severity.INFO to "🔵"
    )

    fun format(errors: List<UnifiedError>): String {
        return errors.mapIndexed { index, error ->
            formatSingle(error, index)
        }.joinToString("\n")
    }

    fun formatSingle(error: UnifiedError, index: Int): String = buildString {
        appendLine("${index + 1}. ${SEVERITY_EMOJI[error.severity]} [${error.severity}] ${error.title}")
        appendLine("   - 시간: ${error.timestamp}")
        error.service?.let { appendLine("   - 서비스: $it") }
        error.namespace?.let { appendLine("   - 네임스페이스: $it") }
        error.pod?.let { appendLine("   - Pod: $it") }
        error.container?.let { appendLine("   - 컨테이너: $it") }
        error.traceId?.let { appendLine("   - Trace ID: $it") }
        val preview = if (error.message.length > 200) error.message.take(200) + "..." else error.message
        appendLine("   - 메시지: $preview")
    }

    fun generateSummary(errors: List<UnifiedError>): String {
        val byService = errors.groupingBy { it.service ?: "unknown" }.eachCount()
        val bySeverity = errors.groupingBy { it.severity.name }.eachCount()

        return buildString {
            appendLine("## 에러 요약")
            appendLine()
            appendLine("총 ${errors.size}개의 에러/로그 발견")
            appendLine()
            appendLine("### 심각도별")
            bySeverity.entries.sortedByDescending { it.value }.forEach {
                appendLine("- ${it.key}: ${it.value}개")
            }
            appendLine()
            appendLine("### 서비스별")
            byService.entries.sortedByDescending { it.value }.forEach {
                appendLine("- ${it.key}: ${it.value}개")
            }
        }
    }
}

/**
 * 트레이스 포맷팅 담당
 */
object TraceFormatter {
    fun format(trace: TraceData): String = buildString {
        appendLine("## 트레이스: ${trace.traceId}")
        appendLine()
        appendLine("- **루트 서비스**: ${trace.rootService}")
        appendLine("- **루트 작업**: ${trace.rootOperation}")
        appendLine("- **시작 시간**: ${trace.startTime}")
        appendLine("- **총 지속 시간**: ${"%.2f".format(trace.duration)}ms")
        appendLine("- **스팬 수**: ${trace.spanCount}개")

        if (trace.spans.isNotEmpty()) {
            appendLine()
            appendLine("### 스팬 목록")
            trace.spans.forEach { span ->
                val emoji = if (span.status == "error") "❌" else "✅"
                appendLine()
                appendLine("$emoji **${span.serviceName}** → ${span.operationName}")
                appendLine("   - 지속 시간: ${"%.2f".format(span.duration)}ms")

                // 주요 속성만 표시
                listOf("http.method", "http.url", "http.status_code", "db.statement").forEach { key ->
                    span.attributes[key]?.let { appendLine("   - $key: $it") }
                }
            }
        }
    }

    fun formatList(traces: List<TraceData>): String {
        if (traces.isEmpty()) return "검색된 트레이스가 없습니다."

        return buildString {
            appendLine("## 트레이스 검색 결과 (${traces.size}개)")
            appendLine()
            traces.forEach { trace ->
                val duration = if (trace.duration >= 1000) {
                    "${"%.2f".format(trace.duration / 1000)}s"
                } else {
                    "${trace.duration.toInt()}ms"
                }
                appendLine("- **${trace.rootService}** / ${trace.rootOperation}")
                appendLine("  - Trace ID: `${trace.traceId}`")
                appendLine("  - 시간: ${trace.startTime}")
                appendLine("  - 지속 시간: $duration")
                appendLine()
            }
        }
    }
}
