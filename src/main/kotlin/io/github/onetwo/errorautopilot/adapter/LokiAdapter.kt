package io.github.onetwo.errorautopilot.adapter

import io.github.onetwo.errorautopilot.model.*
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import java.io.Closeable
import java.net.URLEncoder
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Loki 서버와 통신하여 로그 데이터를 조회하는 어댑터 클래스.
 *
 * Loki API를 사용하여 에러 로그를 검색하고, 결과를 [UnifiedError] 형식으로 변환합니다.
 * 이 어댑터는 [Closeable]을 구현하여 리소스 해제를 보장합니다.
 *
 * @property config Loki 서버 연결 설정
 * @see [Loki HTTP API](https://grafana.com/docs/loki/latest/reference/loki-http-api/)
 *
 * @sample
 * ```kotlin
 * val adapter = LokiAdapter(LokiConfig(url = "http://localhost:3100"))
 * val errors = adapter.fetchErrors(FetchErrorsOptions(sinceMinutes = 60))
 * adapter.close()
 * ```
 */
class LokiAdapter(private val config: LokiConfig) : Closeable {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    /**
     * Loki 서버 연결 상태를 테스트합니다.
     *
     * `/ready` 엔드포인트를 호출하여 서버가 요청을 처리할 준비가 되었는지 확인합니다.
     *
     * @return 연결 성공 시 성공 메시지를 담은 [Result], 실패 시 예외를 담은 [Result]
     */
    suspend fun testConnection(): Result<String> = runCatching {
        val response = client.get("${config.url}/ready")
        if (response.status.isSuccess()) {
            "Loki 연결 성공 (${config.url})"
        } else {
            throw LokiConnectionException("Loki 연결 실패: ${response.status}")
        }
    }

    /**
     * Loki에서 사용 가능한 모든 라벨 이름을 조회합니다.
     *
     * @return 라벨 이름 목록 (예: ["service_name", "namespace", "pod"])
     * @throws Exception Loki API 호출 실패 시
     */
    suspend fun getLabels(): List<String> {
        val response = client.get("${config.url}/loki/api/v1/labels") {
            header(ORG_ID_HEADER, config.orgId)
        }
        val result: LokiLabelsResponse = response.body()
        return result.data
    }

    /**
     * 특정 라벨의 모든 값을 조회합니다.
     *
     * @param label 조회할 라벨 이름 (예: "service_name")
     * @return 해당 라벨의 모든 값 목록
     * @throws Exception Loki API 호출 실패 시
     */
    suspend fun getLabelValues(label: String): List<String> {
        val response = client.get("${config.url}/loki/api/v1/label/$label/values") {
            header(ORG_ID_HEADER, config.orgId)
        }
        val result: LokiLabelsResponse = response.body()
        return result.data
    }

    /**
     * 지정된 옵션에 따라 에러 로그를 조회합니다.
     *
     * LogQL 쿼리를 자동으로 생성하여 Loki에서 에러 로그를 검색합니다.
     * 심각도, 서비스, 네임스페이스 등으로 필터링할 수 있습니다.
     *
     * @param options 조회 옵션 (시간 범위, 심각도, 서비스 등)
     * @return 변환된 [UnifiedError] 목록 (타임스탬프 내림차순 정렬)
     */
    suspend fun fetchErrors(options: FetchErrorsOptions): List<UnifiedError> {
        val logQL = buildQuery(options)
        return query(logQL, options.sinceMinutes, options.limit)
    }

    /**
     * 커스텀 LogQL 쿼리를 실행하여 로그를 조회합니다.
     *
     * @param logQL 실행할 LogQL 쿼리 문자열
     * @param sinceMinutes 현재 시간으로부터의 조회 범위 (분)
     * @param limit 최대 조회 개수
     * @return 변환된 [UnifiedError] 목록 (타임스탬프 내림차순 정렬)
     *
     * @sample
     * ```kotlin
     * val errors = adapter.query(
     *     logQL = "{service_name=\"api-server\"} |= \"error\"",
     *     sinceMinutes = 30,
     *     limit = 50
     * )
     * ```
     */
    suspend fun query(logQL: String, sinceMinutes: Int, limit: Int): List<UnifiedError> {
        val now = Instant.now()
        val start = now.minusSeconds(sinceMinutes * SECONDS_PER_MINUTE)

        val encodedQuery = URLEncoder.encode(logQL, Charsets.UTF_8.name())
        val url = buildString {
            append("${config.url}/loki/api/v1/query_range")
            append("?query=$encodedQuery")
            append("&start=${start.epochSecond}")
            append("&end=${now.epochSecond}")
            append("&limit=$limit")
        }

        logger.debug { "Loki query: $logQL" }

        val response = client.get(url) {
            header(ORG_ID_HEADER, config.orgId)
        }

        val result: LokiQueryResponse = response.body()

        return result.data.result.flatMap { stream ->
            stream.values.map { value ->
                parseLogEntry(stream.stream, value)
            }
        }.sortedByDescending { it.timestamp }
    }

    /**
     * 조회 옵션을 기반으로 LogQL 쿼리 문자열을 생성합니다.
     *
     * @param options 조회 옵션
     * @return 생성된 LogQL 쿼리 문자열
     */
    private fun buildQuery(options: FetchErrorsOptions): String {
        val selectors = mutableListOf<String>()

        // 서비스 필터
        if (options.service != null) {
            selectors.add("service_name=\"${options.service}\"")
        } else {
            selectors.add("service_name=~\".+\"")
        }

        // 네임스페이스 필터
        options.namespace?.let {
            selectors.add("namespace=\"$it\"")
        }

        val selector = selectors.joinToString(", ", "{", "}")

        // 심각도 필터 (case-insensitive 정규식)
        val severityFilter = if (options.severity.isNotEmpty()) {
            val patterns = options.severity.joinToString("|") { it.name.lowercase() }
            " |~ \"(?i)($patterns)\""
        } else {
            ""
        }

        return "$selector$severityFilter"
    }

    /**
     * Loki 스트림 데이터를 [UnifiedError]로 변환합니다.
     *
     * @param stream 라벨 맵
     * @param value [타임스탬프, 메시지] 형식의 리스트
     * @return 변환된 [UnifiedError]
     */
    private fun parseLogEntry(stream: Map<String, String>, value: List<String>): UnifiedError {
        val timestampNanos = value[0].toLong()
        val message = value.getOrElse(1) { "" }

        val severity = detectSeverity(message, stream)
        val title = extractTitle(message)

        return UnifiedError(
            timestamp = Instant.ofEpochSecond(
                timestampNanos / NANOS_PER_SECOND,
                timestampNanos % NANOS_PER_SECOND
            ),
            severity = severity,
            title = title,
            message = message,
            service = stream["service_name"] ?: stream["service.name"],
            namespace = stream["namespace"],
            pod = stream["pod"] ?: stream["k8s.pod.name"],
            traceId = extractTraceId(message, stream),
            labels = stream
        )
    }

    /**
     * 메시지와 라벨에서 심각도를 감지합니다.
     *
     * 라벨의 level/severity 값과 메시지 내용을 분석하여 심각도를 결정합니다.
     *
     * @param message 로그 메시지
     * @param labels 라벨 맵
     * @return 감지된 [Severity]
     */
    private fun detectSeverity(message: String, labels: Map<String, String>): Severity {
        val levelLabel = labels["level"] ?: labels["severity"] ?: ""
        val combined = "$levelLabel $message".lowercase()

        return when {
            "critical" in combined || "fatal" in combined -> Severity.CRITICAL
            "error" in combined || "exception" in combined -> Severity.ERROR
            "warn" in combined -> Severity.WARNING
            else -> Severity.INFO
        }
    }

    /**
     * 메시지에서 제목을 추출합니다.
     *
     * 첫 번째 줄을 추출하고, 100자를 초과하면 잘라냅니다.
     *
     * @param message 전체 로그 메시지
     * @return 추출된 제목 (최대 100자)
     */
    private fun extractTitle(message: String): String {
        val firstLine = message.lines().firstOrNull() ?: message
        return if (firstLine.length > MAX_TITLE_LENGTH) {
            firstLine.take(MAX_TITLE_LENGTH) + "..."
        } else {
            firstLine
        }
    }

    /**
     * 메시지와 라벨에서 Trace ID를 추출합니다.
     *
     * 라벨에서 먼저 확인하고, 없으면 메시지에서 정규식으로 추출합니다.
     *
     * @param message 로그 메시지
     * @param labels 라벨 맵
     * @return 추출된 Trace ID 또는 null
     */
    private fun extractTraceId(message: String, labels: Map<String, String>): String? {
        // 라벨에서 먼저 확인
        labels["trace_id"]?.let { return it }
        labels["traceId"]?.let { return it }

        // 메시지에서 추출 시도 (32자 hex 또는 UUID 형식)
        return TRACE_ID_PATTERN.find(message)?.value
    }

    /**
     * HTTP 클라이언트를 종료하고 리소스를 해제합니다.
     */
    override fun close() {
        client.close()
    }

    companion object {
        /** X-Scope-OrgID 헤더 이름 */
        private const val ORG_ID_HEADER = "X-Scope-OrgID"

        /** 1분당 초 */
        private const val SECONDS_PER_MINUTE = 60L

        /** 1초당 나노초 */
        private const val NANOS_PER_SECOND = 1_000_000_000L

        /** 제목 최대 길이 */
        private const val MAX_TITLE_LENGTH = 100

        /** Trace ID 추출 패턴 (32자 hex 또는 UUID) */
        private val TRACE_ID_PATTERN = Regex("[0-9a-f]{32}|[0-9a-f-]{36}")
    }
}

/**
 * Loki 연결 실패 시 발생하는 예외.
 *
 * @param message 오류 메시지
 */
class LokiConnectionException(message: String) : Exception(message)
