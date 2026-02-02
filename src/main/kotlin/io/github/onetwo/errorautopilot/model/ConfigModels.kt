package io.github.onetwo.errorautopilot.model

import kotlinx.serialization.Serializable

/**
 * 배포 환경을 나타내는 enum.
 *
 * Loki 쿼리 시 deployment_environment 인덱싱된 레이블로 빠른 검색이 가능합니다.
 */
enum class Environment(val label: String, val emoji: String) {
    DEV("dev", "🟢 개발"),
    PROD("prod", "🔴 운영");

    companion object {
        fun fromString(value: String?): Environment =
            when (value?.lowercase()) {
                "prod", "production" -> PROD
                else -> DEV
            }
    }
}

/**
 * Loki 서버 연결 설정을 정의하는 데이터 클래스.
 *
 * @property url Loki 서버 URL (예: "http://localhost:3100")
 * @property orgId 멀티테넌시를 위한 조직 ID (기본: "default")
 * @property environment 배포 환경 (deployment_environment 레이블 필터링에 사용)
 * @property defaultQuery 기본 LogQL 쿼리 (선택적)
 * @property username Basic Auth 사용자명 (선택적)
 * @property password Basic Auth 비밀번호 (선택적)
 */
@Serializable
data class LokiConfig(
    val url: String,
    val orgId: String = DEFAULT_ORG_ID,
    val environment: Environment? = null,
    val defaultQuery: String? = null,
    val username: String? = null,
    val password: String? = null
) {
    init {
        require(url.isNotBlank()) { "Loki URL must not be blank" }
        require(url.startsWith("http://") || url.startsWith("https://")) {
            "Loki URL must start with http:// or https://"
        }
    }

    /**
     * Basic Auth가 설정되었는지 확인합니다.
     *
     * @return username과 password가 모두 설정되어 있으면 true
     */
    fun hasBasicAuth(): Boolean = !username.isNullOrBlank() && !password.isNullOrBlank()

    companion object {
        /** 기본 조직 ID */
        const val DEFAULT_ORG_ID = "default"
    }
}

/**
 * Tempo 서버 연결 설정을 정의하는 데이터 클래스.
 *
 * @property url Tempo 서버 URL (예: "http://localhost:3200")
 * @property orgId 멀티테넌시를 위한 조직 ID (기본: "default")
 * @property username Basic Auth 사용자명 (선택적)
 * @property password Basic Auth 비밀번호 (선택적)
 */
@Serializable
data class TempoConfig(
    val url: String,
    val orgId: String = DEFAULT_ORG_ID,
    val username: String? = null,
    val password: String? = null
) {
    init {
        require(url.isNotBlank()) { "Tempo URL must not be blank" }
        require(url.startsWith("http://") || url.startsWith("https://")) {
            "Tempo URL must start with http:// or https://"
        }
    }

    /**
     * Basic Auth가 설정되었는지 확인합니다.
     *
     * @return username과 password가 모두 설정되어 있으면 true
     */
    fun hasBasicAuth(): Boolean = !username.isNullOrBlank() && !password.isNullOrBlank()

    companion object {
        /** 기본 조직 ID */
        const val DEFAULT_ORG_ID = "default"
    }
}

/**
 * GitHub 리포지토리 설정을 정의하는 데이터 클래스.
 *
 * @property owner GitHub 조직 또는 사용자명
 * @property repo 리포지토리명
 */
@Serializable
data class GithubConfig(
    val owner: String,
    val repo: String
) {
    init {
        require(owner.isNotBlank()) { "GitHub owner must not be blank" }
        require(repo.isNotBlank()) { "GitHub repo must not be blank" }
    }

    /**
     * 전체 리포지토리 경로를 반환합니다.
     *
     * @return "owner/repo" 형식의 문자열
     */
    fun fullName(): String = "$owner/$repo"
}

/**
 * 환경별 설정을 담는 데이터 클래스.
 *
 * @property loki 해당 환경의 Loki 설정
 * @property tempo 해당 환경의 Tempo 설정 (선택적)
 */
data class EnvironmentConfig(
    val loki: LokiConfig,
    val tempo: TempoConfig? = null
)

/**
 * 애플리케이션 전체 설정을 통합하는 데이터 클래스.
 *
 * 환경 변수에서 로드된 설정을 하나의 객체로 관리합니다.
 * dev/prod 환경별 설정을 지원합니다.
 *
 * @property dev 개발 환경 설정
 * @property prod 운영 환경 설정
 * @property github GitHub 설정 (선택적 - 이슈 생성 기능 사용 시 필요)
 */
data class AppConfig(
    val dev: EnvironmentConfig,
    val prod: EnvironmentConfig,
    val github: GithubConfig? = null
) {
    /**
     * 지정된 환경의 설정을 반환합니다.
     *
     * @param env 환경
     * @return 해당 환경의 [EnvironmentConfig]
     */
    fun forEnvironment(env: Environment): EnvironmentConfig =
        when (env) {
            Environment.DEV -> dev
            Environment.PROD -> prod
        }

    /**
     * GitHub 연동이 설정되었는지 확인합니다.
     *
     * @return [github] 설정이 있으면 true
     */
    fun hasGithubIntegration(): Boolean = github != null
}
