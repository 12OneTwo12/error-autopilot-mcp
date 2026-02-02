package io.github.onetwo.errorautopilot.model

import java.time.Instant

/**
 * Tempo에서 조회한 분산 트레이스 데이터를 표현하는 데이터 클래스.
 *
 * 마이크로서비스 간의 요청 흐름을 추적하며, 성능 분석 및 에러 원인 파악에 활용됩니다.
 *
 * @property traceId 트레이스의 고유 식별자 (32자 hex 또는 UUID 형식)
 * @property rootService 요청을 시작한 서비스명
 * @property rootOperation 루트 스팬의 작업명 (예: "GET /api/users")
 * @property startTime 트레이스 시작 시간
 * @property duration 전체 트레이스 소요 시간 (밀리초)
 * @property spanCount 트레이스에 포함된 총 스팬 개수
 * @property spans 개별 스팬 목록
 */
data class TraceData(
    val traceId: String,
    val rootService: String,
    val rootOperation: String,
    val startTime: Instant,
    val duration: Double,
    val spanCount: Int,
    val spans: List<SpanData>
) {
    /**
     * 이 트레이스가 에러를 포함하는지 확인합니다.
     *
     * @return 에러 상태의 스팬이 하나라도 있으면 true
     */
    fun hasError(): Boolean = spans.any { it.isError() }

    /**
     * 지정된 기준 시간보다 오래 걸린 트레이스인지 확인합니다.
     *
     * @param thresholdMs 기준 시간 (밀리초)
     * @return [duration]이 [thresholdMs]보다 크면 true
     */
    fun isSlow(thresholdMs: Double = 1000.0): Boolean = duration > thresholdMs

    /**
     * 특정 서비스의 스팬만 필터링합니다.
     *
     * @param serviceName 필터링할 서비스명
     * @return 해당 서비스의 스팬 목록
     */
    fun filterByService(serviceName: String): List<SpanData> =
        spans.filter { it.serviceName == serviceName }

    companion object {
        /** 느린 트레이스로 판단하는 기본 임계값 (밀리초) */
        const val DEFAULT_SLOW_THRESHOLD_MS = 1000.0
    }
}

/**
 * 트레이스 내의 개별 스팬 데이터를 표현하는 데이터 클래스.
 *
 * 스팬은 트레이스 내에서 단일 작업 단위를 나타내며,
 * 서비스 호출, DB 쿼리, HTTP 요청 등을 추적합니다.
 *
 * @property spanId 스팬의 고유 식별자
 * @property parentSpanId 부모 스팬의 ID (루트 스팬이면 null)
 * @property serviceName 이 스팬이 속한 서비스명
 * @property operationName 작업 이름 (예: "HTTP GET", "SELECT users")
 * @property startTime 스팬 시작 시간
 * @property duration 스팬 소요 시간 (밀리초)
 * @property status 스팬 상태 ("ok" 또는 "error")
 * @property attributes 추가 속성 맵 (http.method, db.statement 등)
 */
data class SpanData(
    val spanId: String,
    val parentSpanId: String? = null,
    val serviceName: String,
    val operationName: String,
    val startTime: Instant? = null,
    val duration: Double,
    val status: String,
    val attributes: Map<String, String> = emptyMap()
) {
    /**
     * 이 스팬이 에러 상태인지 확인합니다.
     *
     * @return [status]가 "error"이면 true
     */
    fun isError(): Boolean = status == STATUS_ERROR

    /**
     * 이 스팬이 루트 스팬인지 확인합니다.
     *
     * @return [parentSpanId]가 null이면 true
     */
    fun isRoot(): Boolean = parentSpanId == null

    /**
     * HTTP 관련 속성을 추출합니다.
     *
     * @return HTTP method, URL, status code를 포함한 맵
     */
    fun getHttpAttributes(): Map<String, String> =
        attributes.filterKeys { it.startsWith("http.") }

    /**
     * DB 관련 속성을 추출합니다.
     *
     * @return DB statement, type 등을 포함한 맵
     */
    fun getDbAttributes(): Map<String, String> =
        attributes.filterKeys { it.startsWith("db.") }

    companion object {
        /** 정상 상태 값 */
        const val STATUS_OK = "ok"

        /** 에러 상태 값 */
        const val STATUS_ERROR = "error"
    }
}
