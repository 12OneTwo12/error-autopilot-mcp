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
import kotlinx.serialization.json.*
import java.io.Closeable
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Tempo 서버와 통신하여 분산 트레이스 데이터를 조회하는 어댑터.
 *
 * Tempo API를 통해 Trace ID로 개별 트레이스를 조회하거나 조건에 맞는 트레이스를 검색합니다.
 * [Closeable]을 구현하므로 사용 후 반드시 close()를 호출해야 합니다.
 *
 * @property config Tempo 서버 연결 설정
 * @see [Tempo HTTP API](https://grafana.com/docs/tempo/latest/api_docs/)
 */
class TempoAdapter(private val config: TempoConfig) : Closeable {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    /**
     * Tempo 서버 연결 상태를 테스트합니다.
     *
     * `/ready` 엔드포인트를 호출하여 서버가 요청을 처리할 준비가 되었는지 확인합니다.
     *
     * @return 연결 성공 시 성공 메시지를 담은 [Result], 실패 시 예외를 담은 [Result]
     */
    suspend fun testConnection(): Result<String> = runCatching {
        val response = client.get("${config.url}/ready")
        if (response.status.isSuccess()) {
            "Tempo 연결 성공 (${config.url})"
        } else {
            throw TempoConnectionException("Tempo 연결 실패: ${response.status}")
        }
    }

    /**
     * Trace ID로 특정 트레이스를 조회합니다.
     *
     * @param traceId 조회할 트레이스의 ID (32자 hex 형식)
     * @return 조회된 [TraceData] 또는 찾지 못한 경우 null
     */
    suspend fun getTrace(traceId: String): TraceData? {
        return try {
            val response = client.get("${config.url}/api/traces/$traceId") {
                header(ORG_ID_HEADER, config.orgId)
            }

            if (!response.status.isSuccess()) {
                logger.warn { "Trace not found: $traceId" }
                return null
            }

            val jsonResponse: JsonObject = response.body()
            parseTraceResponse(traceId, jsonResponse)
        } catch (e: Exception) {
            logger.error(e) { "Failed to get trace: $traceId" }
            null
        }
    }

    /**
     * 조건에 맞는 트레이스를 검색합니다.
     *
     * @param service 필터링할 서비스명 (null이면 전체)
     * @param sinceMinutes 현재 시간으로부터의 검색 범위 (분, 기본: 60)
     * @param minDuration 최소 지속 시간 필터 (예: "1s", "500ms")
     * @param maxDuration 최대 지속 시간 필터 (예: "10s")
     * @param limit 최대 조회 개수 (기본: 20)
     * @return 검색된 [TraceData] 목록
     */
    suspend fun searchTraces(
        service: String? = null,
        sinceMinutes: Int = DEFAULT_SINCE_MINUTES,
        minDuration: String? = null,
        maxDuration: String? = null,
        limit: Int = DEFAULT_LIMIT
    ): List<TraceData> {
        val now = Instant.now().epochSecond
        val start = now - (sinceMinutes * SECONDS_PER_MINUTE)

        val queryString = buildSearchQueryString(start, now, service, minDuration, maxDuration, limit)

        return try {
            val response = client.get("${config.url}/api/search$queryString") {
                header(ORG_ID_HEADER, config.orgId)
            }

            val jsonResponse: JsonObject = response.body()
            val traces = jsonResponse["traces"]?.jsonArray ?: return emptyList()

            traces.mapNotNull { trace ->
                try {
                    parseSearchResult(trace.jsonObject)
                } catch (e: Exception) {
                    logger.warn(e) { "Failed to parse trace" }
                    null
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to search traces" }
            emptyList()
        }
    }

    /**
     * 트레이스 검색을 위한 쿼리 문자열을 생성합니다.
     */
    private fun buildSearchQueryString(
        start: Long,
        end: Long,
        service: String?,
        minDuration: String?,
        maxDuration: String?,
        limit: Int
    ): String {
        val params = mutableListOf<String>()
        service?.let { params.add("service.name=$it") }
        minDuration?.let { params.add("minDuration=$it") }
        maxDuration?.let { params.add("maxDuration=$it") }

        return buildString {
            append("?start=$start&end=$end&limit=$limit")
            if (params.isNotEmpty()) {
                append("&tags=${params.joinToString("&")}")
            }
        }
    }

    /**
     * Tempo API 응답을 [TraceData]로 변환합니다.
     *
     * @param traceId 트레이스 ID
     * @param response Tempo API JSON 응답
     * @return 변환된 [TraceData] 또는 파싱 실패 시 null
     */
    private fun parseTraceResponse(traceId: String, response: JsonObject): TraceData? {
        val batches = response["batches"]?.jsonArray ?: return null

        val spans = mutableListOf<SpanData>()
        var rootService = UNKNOWN_VALUE
        var rootOperation = UNKNOWN_VALUE
        var startTimeNanos = Long.MAX_VALUE
        var endTimeNanos = 0L

        for (batch in batches) {
            val batchObj = batch.jsonObject
            val serviceName = extractServiceName(batchObj)
            val scopeSpans = batchObj["scopeSpans"]?.jsonArray ?: continue

            for (scopeSpan in scopeSpans) {
                val spanList = scopeSpan.jsonObject["spans"]?.jsonArray ?: continue

                for (spanJson in spanList) {
                    val span = spanJson.jsonObject
                    val spanStartTime = span["startTimeUnixNano"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0
                    val spanEndTime = span["endTimeUnixNano"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0

                    // 루트 스팬 추적 (가장 빠른 시작 시간)
                    if (spanStartTime < startTimeNanos) {
                        startTimeNanos = spanStartTime
                        rootService = serviceName
                        rootOperation = span["name"]?.jsonPrimitive?.content ?: UNKNOWN_VALUE
                    }
                    if (spanEndTime > endTimeNanos) {
                        endTimeNanos = spanEndTime
                    }

                    spans.add(parseSpan(span, serviceName, spanStartTime, spanEndTime))
                }
            }
        }

        if (spans.isEmpty()) return null

        return TraceData(
            traceId = traceId,
            rootService = rootService,
            rootOperation = rootOperation,
            startTime = Instant.ofEpochSecond(startTimeNanos / NANOS_PER_SECOND),
            duration = (endTimeNanos - startTimeNanos) / NANOS_PER_MILLIS.toDouble(),
            spanCount = spans.size,
            spans = spans
        )
    }

    /**
     * 배치에서 서비스명을 추출합니다.
     */
    private fun extractServiceName(batchObj: JsonObject): String {
        return batchObj["resource"]?.jsonObject
            ?.get("attributes")?.jsonArray
            ?.find { it.jsonObject["key"]?.jsonPrimitive?.content == "service.name" }
            ?.jsonObject?.get("value")?.jsonObject?.get("stringValue")?.jsonPrimitive?.content
            ?: UNKNOWN_VALUE
    }

    /**
     * JSON 스팬 객체를 [SpanData]로 변환합니다.
     */
    private fun parseSpan(
        span: JsonObject,
        serviceName: String,
        spanStartTime: Long,
        spanEndTime: Long
    ): SpanData {
        val attributes = span["attributes"]?.jsonArray?.associate {
            val key = it.jsonObject["key"]?.jsonPrimitive?.content ?: ""
            val value = it.jsonObject["value"]?.jsonObject?.values?.firstOrNull()
                ?.jsonPrimitive?.content ?: ""
            key to value
        } ?: emptyMap()

        val status = span["status"]?.jsonObject?.get("code")?.jsonPrimitive?.content
        val isError = status == OTEL_ERROR_STATUS_CODE

        return SpanData(
            spanId = span["spanId"]?.jsonPrimitive?.content ?: "",
            serviceName = serviceName,
            operationName = span["name"]?.jsonPrimitive?.content ?: "",
            duration = (spanEndTime - spanStartTime) / NANOS_PER_MILLIS.toDouble(),
            status = if (isError) SpanData.STATUS_ERROR else SpanData.STATUS_OK,
            attributes = attributes
        )
    }

    /**
     * 검색 결과를 [TraceData]로 변환합니다 (스팬 목록 없음).
     */
    private fun parseSearchResult(trace: JsonObject): TraceData {
        val traceId = trace["traceID"]?.jsonPrimitive?.content ?: ""
        val rootServiceName = trace["rootServiceName"]?.jsonPrimitive?.content ?: UNKNOWN_VALUE
        val rootTraceName = trace["rootTraceName"]?.jsonPrimitive?.content ?: UNKNOWN_VALUE
        val startTimeUnixNano = trace["startTimeUnixNano"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0
        val durationMs = trace["durationMs"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0

        return TraceData(
            traceId = traceId,
            rootService = rootServiceName,
            rootOperation = rootTraceName,
            startTime = Instant.ofEpochSecond(startTimeUnixNano / NANOS_PER_SECOND),
            duration = durationMs,
            spanCount = 0,
            spans = emptyList()
        )
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

        /** 알 수 없는 값 표시 */
        private const val UNKNOWN_VALUE = "unknown"

        /** 기본 조회 시간 범위 (분) */
        private const val DEFAULT_SINCE_MINUTES = 60

        /** 기본 조회 제한 개수 */
        private const val DEFAULT_LIMIT = 20

        /** 1분당 초 */
        private const val SECONDS_PER_MINUTE = 60

        /** 1초당 나노초 */
        private const val NANOS_PER_SECOND = 1_000_000_000L

        /** 1밀리초당 나노초 */
        private const val NANOS_PER_MILLIS = 1_000_000L

        /** OpenTelemetry 에러 상태 코드 */
        private const val OTEL_ERROR_STATUS_CODE = "2"
    }
}

/**
 * Tempo 연결 실패 시 발생하는 예외.
 *
 * @param message 오류 메시지
 */
class TempoConnectionException(message: String) : Exception(message)
