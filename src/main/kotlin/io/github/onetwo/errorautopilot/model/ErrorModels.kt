package io.github.onetwo.errorautopilot.model

import java.time.Instant
import java.util.UUID

/**
 * Loki에서 수집된 에러 로그를 통합된 형식으로 표현하는 데이터 클래스.
 *
 * 다양한 소스(Loki, Kubernetes 등)에서 수집된 로그 데이터를 일관된 형식으로 정규화하여
 * 분석 및 이슈 생성에 활용됩니다.
 *
 * @property id 에러의 고유 식별자 (UUID)
 * @property source 에러의 출처 (기본값: "loki")
 * @property timestamp 에러 발생 시간
 * @property severity 에러의 심각도 수준
 * @property title 에러의 요약 제목 (첫 번째 줄 또는 100자 제한)
 * @property message 전체 에러 메시지
 * @property stackTrace 스택 트레이스 (있는 경우)
 * @property service 에러가 발생한 서비스명
 * @property namespace Kubernetes 네임스페이스
 * @property pod 에러가 발생한 Pod 이름
 * @property container 컨테이너 이름
 * @property traceId 분산 트레이싱을 위한 Trace ID
 * @property labels Loki 로그 스트림의 라벨 맵
 * @property raw 원본 로그 데이터 (디버깅용)
 */
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
) {
    /**
     * 이 에러가 심각한 수준인지 확인합니다.
     *
     * @return [Severity.CRITICAL] 또는 [Severity.ERROR]인 경우 true
     */
    fun isCritical(): Boolean = severity == Severity.CRITICAL || severity == Severity.ERROR

    /**
     * 분산 트레이싱 정보가 있는지 확인합니다.
     *
     * @return [traceId]가 null이 아니면 true
     */
    fun hasTraceId(): Boolean = traceId != null
}

/**
 * 에러 로그 조회 시 사용되는 옵션을 정의하는 데이터 클래스.
 *
 * Loki API 호출 시 필터링 조건을 지정하는 데 사용됩니다.
 *
 * @property sinceMinutes 현재 시간으로부터 몇 분 이내의 로그를 조회할지 지정 (기본: 60분)
 * @property severity 조회할 심각도 수준 목록 (기본: ERROR, CRITICAL)
 * @property service 특정 서비스만 필터링 (null이면 전체)
 * @property namespace 특정 네임스페이스만 필터링 (null이면 전체)
 * @property limit 최대 조회 개수 (기본: 100)
 */
data class FetchErrorsOptions(
    val sinceMinutes: Int = DEFAULT_SINCE_MINUTES,
    val severity: List<Severity> = DEFAULT_SEVERITY,
    val service: String? = null,
    val namespace: String? = null,
    val limit: Int = DEFAULT_LIMIT
) {
    init {
        require(sinceMinutes > 0) { "sinceMinutes must be positive" }
        require(limit > 0) { "limit must be positive" }
        require(limit <= MAX_LIMIT) { "limit cannot exceed $MAX_LIMIT" }
    }

    companion object {
        /** 기본 조회 시간 범위 (분) */
        const val DEFAULT_SINCE_MINUTES = 60

        /** 기본 조회 제한 개수 */
        const val DEFAULT_LIMIT = 100

        /** 최대 조회 제한 개수 */
        const val MAX_LIMIT = 10000

        /** 기본 심각도 필터 */
        val DEFAULT_SEVERITY = listOf(Severity.ERROR, Severity.CRITICAL)
    }
}
