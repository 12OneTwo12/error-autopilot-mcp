package io.github.onetwo.errorautopilot.formatter

import io.github.onetwo.errorautopilot.model.Severity
import io.github.onetwo.errorautopilot.model.TraceData
import io.github.onetwo.errorautopilot.model.UnifiedError

/**
 * 에러 로그를 사람이 읽기 쉬운 형식으로 포맷팅하는 유틸리티 객체.
 *
 * [UnifiedError] 목록을 Markdown 형식의 문자열로 변환하며,
 * 에러 요약 정보도 생성합니다.
 *
 * @sample
 * ```kotlin
 * val errors = listOf(error1, error2)
 * val formatted = ErrorFormatter.format(errors)
 * val summary = ErrorFormatter.generateSummary(errors)
 * ```
 */
object ErrorFormatter {

    /** 심각도별 이모지 매핑 */
    private val SEVERITY_EMOJI = mapOf(
        Severity.CRITICAL to "🔴",
        Severity.ERROR to "🟠",
        Severity.WARNING to "🟡",
        Severity.INFO to "🔵"
    )

    /** 메시지 미리보기 최대 길이 */
    private const val MESSAGE_PREVIEW_LENGTH = 200

    /**
     * 에러 목록을 Markdown 형식으로 포맷팅합니다.
     *
     * @param errors 포맷팅할 에러 목록
     * @return Markdown 형식의 에러 목록 문자열
     */
    fun format(errors: List<UnifiedError>): String {
        return errors.mapIndexed { index, error ->
            formatSingle(error, index)
        }.joinToString("\n")
    }

    /**
     * 단일 에러를 Markdown 형식으로 포맷팅합니다.
     *
     * @param error 포맷팅할 에러
     * @param index 에러의 순서 번호 (0-based)
     * @return Markdown 형식의 에러 문자열
     */
    fun formatSingle(error: UnifiedError, index: Int): String = buildString {
        val emoji = SEVERITY_EMOJI[error.severity] ?: "⚪"
        appendLine("${index + 1}. $emoji [${error.severity}] ${error.title}")
        appendLine("   - 시간: ${error.timestamp}")
        error.service?.let { appendLine("   - 서비스: $it") }
        error.namespace?.let { appendLine("   - 네임스페이스: $it") }
        error.pod?.let { appendLine("   - Pod: $it") }
        error.container?.let { appendLine("   - 컨테이너: $it") }
        error.traceId?.let { appendLine("   - Trace ID: $it") }

        val preview = truncateMessage(error.message, MESSAGE_PREVIEW_LENGTH)
        appendLine("   - 메시지: $preview")
    }

    /**
     * 에러 목록의 요약 정보를 생성합니다.
     *
     * 심각도별, 서비스별 에러 개수를 집계하여 Markdown 형식으로 반환합니다.
     *
     * @param errors 요약할 에러 목록
     * @return Markdown 형식의 요약 문자열
     */
    fun generateSummary(errors: List<UnifiedError>): String {
        val byService = errors.groupingBy { it.service ?: "unknown" }.eachCount()
        val bySeverity = errors.groupingBy { it.severity.name }.eachCount()

        return buildString {
            appendLine("## 에러 요약")
            appendLine()
            appendLine("총 ${errors.size}개의 에러/로그 발견")
            appendLine()
            appendLine("### 심각도별")
            bySeverity.entries
                .sortedByDescending { it.value }
                .forEach { appendLine("- ${it.key}: ${it.value}개") }
            appendLine()
            appendLine("### 서비스별")
            byService.entries
                .sortedByDescending { it.value }
                .forEach { appendLine("- ${it.key}: ${it.value}개") }
        }
    }

    /**
     * 메시지를 지정된 길이로 자릅니다.
     *
     * @param message 원본 메시지
     * @param maxLength 최대 길이
     * @return 잘린 메시지 (초과 시 "..." 추가)
     */
    private fun truncateMessage(message: String, maxLength: Int): String {
        return if (message.length > maxLength) {
            message.take(maxLength) + "..."
        } else {
            message
        }
    }
}

/**
 * 분산 트레이스 데이터를 사람이 읽기 쉬운 형식으로 포맷팅하는 유틸리티 객체.
 *
 * [TraceData]를 Markdown 형식의 문자열로 변환하며,
 * 개별 트레이스 상세 정보와 목록 형식 모두 지원합니다.
 *
 * @sample
 * ```kotlin
 * val trace = tempoAdapter.getTrace("abc123...")
 * val formatted = TraceFormatter.format(trace!!)
 * ```
 */
object TraceFormatter {

    /** 표시할 주요 속성 키 목록 */
    private val IMPORTANT_ATTRIBUTES = listOf(
        "http.method",
        "http.url",
        "http.status_code",
        "db.statement"
    )

    /** 밀리초를 초로 변환하는 임계값 */
    private const val MS_TO_SECONDS_THRESHOLD = 1000.0

    /**
     * 단일 트레이스를 상세 Markdown 형식으로 포맷팅합니다.
     *
     * 트레이스 메타데이터와 모든 스팬 정보를 포함합니다.
     *
     * @param trace 포맷팅할 트레이스
     * @return Markdown 형식의 트레이스 상세 정보
     */
    fun format(trace: TraceData): String = buildString {
        appendLine("## 트레이스: ${trace.traceId}")
        appendLine()
        appendLine("- **루트 서비스**: ${trace.rootService}")
        appendLine("- **루트 작업**: ${trace.rootOperation}")
        appendLine("- **시작 시간**: ${trace.startTime}")
        appendLine("- **총 지속 시간**: ${formatDuration(trace.duration)}")
        appendLine("- **스팬 수**: ${trace.spanCount}개")

        if (trace.spans.isNotEmpty()) {
            appendLine()
            appendLine("### 스팬 목록")
            trace.spans.forEach { span ->
                val statusEmoji = if (span.isError()) "❌" else "✅"
                appendLine()
                appendLine("$statusEmoji **${span.serviceName}** → ${span.operationName}")
                appendLine("   - 지속 시간: ${formatDuration(span.duration)}")

                // 주요 속성만 표시
                IMPORTANT_ATTRIBUTES.forEach { key ->
                    span.attributes[key]?.let { appendLine("   - $key: $it") }
                }
            }
        }
    }

    /**
     * 트레이스 목록을 요약 형식으로 포맷팅합니다.
     *
     * 검색 결과 등에서 여러 트레이스를 간략히 표시할 때 사용합니다.
     *
     * @param traces 포맷팅할 트레이스 목록
     * @return Markdown 형식의 트레이스 목록 (빈 목록인 경우 안내 메시지)
     */
    fun formatList(traces: List<TraceData>): String {
        if (traces.isEmpty()) {
            return "검색된 트레이스가 없습니다."
        }

        return buildString {
            appendLine("## 트레이스 검색 결과 (${traces.size}개)")
            appendLine()
            traces.forEach { trace ->
                appendLine("- **${trace.rootService}** / ${trace.rootOperation}")
                appendLine("  - Trace ID: `${trace.traceId}`")
                appendLine("  - 시간: ${trace.startTime}")
                appendLine("  - 지속 시간: ${formatDuration(trace.duration)}")
                appendLine()
            }
        }
    }

    /**
     * 지속 시간을 사람이 읽기 쉬운 형식으로 포맷팅합니다.
     *
     * @param durationMs 밀리초 단위의 지속 시간
     * @return 포맷팅된 문자열 (예: "1.50s", "500ms")
     */
    private fun formatDuration(durationMs: Double): String {
        return if (durationMs >= MS_TO_SECONDS_THRESHOLD) {
            "${"%.2f".format(durationMs / MS_TO_SECONDS_THRESHOLD)}s"
        } else {
            "${durationMs.toInt()}ms"
        }
    }
}
