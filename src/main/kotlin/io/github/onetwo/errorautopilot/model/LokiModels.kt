package io.github.onetwo.errorautopilot.model

import kotlinx.serialization.Serializable

/**
 * Loki query_range API 응답을 표현하는 데이터 클래스.
 *
 * @property status API 호출 상태 ("success" 또는 "error")
 * @property data 실제 쿼리 결과 데이터
 * @see [Loki API Documentation](https://grafana.com/docs/loki/latest/reference/loki-http-api/)
 */
@Serializable
data class LokiQueryResponse(
    val status: String,
    val data: LokiData
) {
    /**
     * 쿼리가 성공했는지 확인합니다.
     *
     * @return [status]가 "success"이면 true
     */
    fun isSuccess(): Boolean = status == STATUS_SUCCESS

    /**
     * 결과가 비어있는지 확인합니다.
     *
     * @return 스트림 결과가 없으면 true
     */
    fun isEmpty(): Boolean = data.result.isEmpty()

    companion object {
        /** 성공 상태 값 */
        const val STATUS_SUCCESS = "success"

        /** 에러 상태 값 */
        const val STATUS_ERROR = "error"
    }
}

/**
 * Loki 쿼리 결과 데이터 컨테이너.
 *
 * @property resultType 결과 유형 ("streams" 또는 "matrix")
 * @property result 로그 스트림 목록
 */
@Serializable
data class LokiData(
    val resultType: String,
    val result: List<LokiStream>
) {
    /**
     * 결과가 스트림 타입인지 확인합니다.
     *
     * @return [resultType]이 "streams"이면 true
     */
    fun isStreamsResult(): Boolean = resultType == RESULT_TYPE_STREAMS

    /**
     * 모든 스트림의 로그 값 개수를 합산합니다.
     *
     * @return 전체 로그 라인 개수
     */
    fun totalLogCount(): Int = result.sumOf { it.values.size }

    companion object {
        /** 스트림 결과 타입 */
        const val RESULT_TYPE_STREAMS = "streams"

        /** 매트릭스 결과 타입 */
        const val RESULT_TYPE_MATRIX = "matrix"
    }
}

/**
 * Loki 로그 스트림을 표현하는 데이터 클래스.
 *
 * 동일한 라벨 셋을 가진 로그 라인들의 집합입니다.
 *
 * @property stream 라벨 키-값 맵 (예: {"service_name": "api-server", "namespace": "default"})
 * @property values 로그 값 목록 - 각 항목은 [timestamp(나노초), message] 형식의 리스트
 */
@Serializable
data class LokiStream(
    val stream: Map<String, String>,
    val values: List<List<String>>
) {
    /**
     * 서비스명을 추출합니다.
     *
     * Loki에서 서비스명은 "service_name" 또는 "service.name" 라벨로 저장됩니다.
     *
     * @return 서비스명 또는 null
     */
    fun getServiceName(): String? =
        stream["service_name"] ?: stream["service.name"]

    /**
     * 네임스페이스를 추출합니다.
     *
     * @return 네임스페이스명 또는 null
     */
    fun getNamespace(): String? = stream["namespace"]

    /**
     * Pod 이름을 추출합니다.
     *
     * Kubernetes 환경에서는 "pod" 또는 "k8s.pod.name" 라벨로 저장됩니다.
     *
     * @return Pod 이름 또는 null
     */
    fun getPodName(): String? =
        stream["pod"] ?: stream["k8s.pod.name"]
}

/**
 * Loki labels API 응답을 표현하는 데이터 클래스.
 *
 * @property status API 호출 상태 ("success" 또는 "error")
 * @property data 라벨 이름 또는 값 목록
 */
@Serializable
data class LokiLabelsResponse(
    val status: String,
    val data: List<String>
) {
    /**
     * 쿼리가 성공했는지 확인합니다.
     *
     * @return [status]가 "success"이면 true
     */
    fun isSuccess(): Boolean = status == LokiQueryResponse.STATUS_SUCCESS

    /**
     * 결과가 비어있는지 확인합니다.
     *
     * @return 라벨 목록이 비어있으면 true
     */
    fun isEmpty(): Boolean = data.isEmpty()
}
