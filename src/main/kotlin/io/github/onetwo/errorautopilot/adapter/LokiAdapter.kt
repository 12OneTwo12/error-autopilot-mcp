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
 * Loki 서버와 통신하여 로그 데이터를 조회하는 어댑터.
 *
 * Loki API를 통해 에러 로그를 검색하고, [UnifiedError] 형식으로 변환합니다.
 * [Closeable]을 구현하므로 사용 후 반드시 close()를 호출해야 합니다.
 *
 * @property config Loki 서버 연결 설정
 * @see [Loki HTTP API](https://grafana.com/docs/loki/latest/reference/loki-http-api/)
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
     * deployment_environment는 인덱싱된 레이블로 빠른 검색이 가능합니다.
     * level은 structured metadata이므로 파이프라인 필터로 처리합니다.
     * 예: {deployment_environment="dev"} | level=~"error|ERROR"
     *
     * @param options 조회 옵션
     * @return 생성된 LogQL 쿼리 문자열
     */
    private fun buildQuery(options: FetchErrorsOptions): String {
        val labelMatchers = mutableListOf<String>()
        val pipelineFilters = mutableListOf<String>()

        // 환경 필터 (인덱싱된 레이블 - 빠른 검색)
        config.environment?.let {
            labelMatchers.add("deployment_environment=\"${it.label}\"")
        }

        // 서비스 필터
        if (options.service != null) {
            labelMatchers.add("service_name=\"${options.service}\"")
        }

        // 네임스페이스 필터
        options.namespace?.let {
            labelMatchers.add("k8s_namespace_name=\"$it\"")
        }

        // 최소한 하나의 label matcher가 필요함 (environment가 없는 경우 fallback)
        if (labelMatchers.isEmpty()) {
            labelMatchers.add("service_name=~\".+\"")
        }

        // severity 필터 (structured metadata - 파이프라인에서 처리)
        // level은 소문자/대문자 모두 매칭
        if (options.severity.isNotEmpty()) {
            val severityPattern = options.severity
                .flatMap { listOf(it.name.lowercase(), it.name.uppercase()) }
                .joinToString("|")
            pipelineFilters.add("level=~\"$severityPattern\"")
        } else {
            // 기본: error, critical 레벨
            pipelineFilters.add("level=~\"error|ERROR|critical|CRITICAL\"")
        }

        val labelSelector = labelMatchers.joinToString(", ", "{", "}")
        val pipeline = if (pipelineFilters.isNotEmpty()) {
            " | ${pipelineFilters.joinToString(" | ")}"
        } else {
            ""
        }

        return "$labelSelector$pipeline"
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
     * level structured metadata를 우선 사용하고, 없으면 severity_text 레이블을 확인합니다.
     *
     * @param message 로그 메시지
     * @param labels 라벨 맵
     * @return 감지된 [Severity]
     */
    private fun detectSeverity(message: String, labels: Map<String, String>): Severity {
        // level structured metadata 우선, 그 다음 severity_text 레이블
        val levelLabel = labels["level"] ?: labels["severity_text"] ?: labels["severity"] ?: ""

        return when (levelLabel.lowercase()) {
            "critical", "fatal" -> Severity.CRITICAL
            "error", "err" -> Severity.ERROR
            "warning", "warn" -> Severity.WARNING
            "info" -> Severity.INFO
            else -> {
                // 레이블에서 찾지 못하면 메시지에서 추출
                val combined = "$levelLabel $message".lowercase()
                when {
                    "critical" in combined || "fatal" in combined -> Severity.CRITICAL
                    "error" in combined || "exception" in combined -> Severity.ERROR
                    "warn" in combined -> Severity.WARNING
                    else -> Severity.INFO
                }
            }
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
