package io.github.onetwo.errorautopilot.model

import kotlinx.serialization.Serializable

/**
 * Loki 서버 연결 설정을 정의하는 데이터 클래스.
 *
 * @property url Loki 서버 URL (예: "http://localhost:3100")
 * @property orgId 멀티테넌시를 위한 조직 ID (기본: "default")
 * @property defaultQuery 기본 LogQL 쿼리 (선택적)
 * @property username Basic Auth 사용자명 (선택적)
 * @property password Basic Auth 비밀번호 (선택적)
 */
@Serializable
data class LokiConfig(
    val url: String,
    val orgId: String = DEFAULT_ORG_ID,
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
 * 애플리케이션 전체 설정을 통합하는 데이터 클래스.
 *
 * 환경 변수에서 로드된 설정을 하나의 객체로 관리합니다.
 *
 * @property loki Loki 서버 설정 (필수)
 * @property tempo Tempo 서버 설정 (선택적 - 트레이싱 기능 사용 시 필요)
 * @property github GitHub 설정 (선택적 - 이슈 생성 기능 사용 시 필요)
 */
data class AppConfig(
    val loki: LokiConfig,
    val tempo: TempoConfig? = null,
    val github: GithubConfig? = null
) {
    /**
     * Tempo 연동이 설정되었는지 확인합니다.
     *
     * @return [tempo] 설정이 있으면 true
     */
    fun hasTempoIntegration(): Boolean = tempo != null

    /**
     * GitHub 연동이 설정되었는지 확인합니다.
     *
     * @return [github] 설정이 있으면 true
     */
    fun hasGithubIntegration(): Boolean = github != null
}
