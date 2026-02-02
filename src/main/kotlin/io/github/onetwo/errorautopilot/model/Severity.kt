package io.github.onetwo.errorautopilot.model

/**
 * 에러 심각도를 나타내는 열거형 클래스.
 *
 * Loki/Tempo에서 수집된 로그의 심각도 수준을 표현하며,
 * 높은 심각도부터 낮은 심각도 순으로 정렬됨.
 *
 * @property CRITICAL 시스템 장애 수준의 심각한 오류
 * @property ERROR 기능 동작에 영향을 주는 오류
 * @property WARNING 잠재적 문제를 나타내는 경고
 * @property INFO 일반적인 정보성 로그
 */
enum class Severity {
    CRITICAL,
    ERROR,
    WARNING,
    INFO;

    companion object {
        /**
         * 문자열을 [Severity] 열거형으로 변환합니다.
         *
         * 대소문자를 구분하지 않고 변환하며, 유효하지 않은 값은 null을 반환합니다.
         *
         * @param value 변환할 문자열 (예: "error", "ERROR", "Error")
         * @return 매핑된 [Severity] 또는 유효하지 않은 경우 null
         *
         * @sample
         * ```kotlin
         * Severity.fromString("error")    // Severity.ERROR
         * Severity.fromString("CRITICAL") // Severity.CRITICAL
         * Severity.fromString("unknown")  // null
         * ```
         */
        fun fromString(value: String): Severity? = try {
            valueOf(value.uppercase())
        } catch (_: IllegalArgumentException) {
            null
        }

        /**
         * 문자열을 [Severity]로 변환하며, 실패 시 기본값을 반환합니다.
         *
         * @param value 변환할 문자열
         * @param default 변환 실패 시 반환할 기본값 (기본: [INFO])
         * @return 매핑된 [Severity] 또는 기본값
         */
        fun fromStringOrDefault(value: String, default: Severity = INFO): Severity =
            fromString(value) ?: default
    }
}
