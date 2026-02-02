package io.github.onetwo.errorautopilot

import io.github.onetwo.errorautopilot.adapter.LokiAdapter
import io.github.onetwo.errorautopilot.adapter.TempoAdapter
import io.github.onetwo.errorautopilot.model.*
import io.github.onetwo.errorautopilot.template.TemplateManager
import io.github.onetwo.errorautopilot.tool.ToolRegistry
import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered

private val logger = KotlinLogging.logger {}

/** 서버 이름 */
private const val SERVER_NAME = "error-autopilot-mcp"

/** 서버 버전 */
private const val SERVER_VERSION = "1.0.0"

/**
 * 환경별 어댑터 그룹.
 */
data class Adapters(
    val loki: LokiAdapter,
    val tempo: TempoAdapter?
)

/**
 * Error Autopilot MCP 서버의 메인 진입점.
 *
 * 이 서버는 Loki/Tempo와 연동하여 에러 로그 분석 및 GitHub 이슈 생성을 자동화합니다.
 * MCP (Model Context Protocol)를 통해 Claude Code와 통합됩니다.
 *
 * ## 환경 변수
 * ### 개발 환경
 * - `LOKI_URL_DEV`: 개발 Loki 서버 URL (기본: http://localhost:3100)
 * - `TEMPO_URL_DEV`: 개발 Tempo 서버 URL (기본: http://localhost:3200)
 *
 * ### 운영 환경
 * - `LOKI_URL_PROD`: 운영 Loki 서버 URL (기본: http://localhost:3100)
 * - `TEMPO_URL_PROD`: 운영 Tempo 서버 URL (기본: http://localhost:3200)
 *
 * ### 공통
 * - `LOKI_ORG_ID`: Loki 멀티테넌시 조직 ID (선택, 기본: default)
 * - `TEMPO_ORG_ID`: Tempo 멀티테넌시 조직 ID (선택, 기본: default)
 * - `GITHUB_REPO`: GitHub 리포지토리명 (선택)
 * - `GITHUB_OWNER`: GitHub 조직/사용자명 (선택)
 *
 * ## 제공하는 도구 (14개)
 * 모든 Loki/Tempo 도구는 `env` 파라미터를 지원합니다 (dev/prod, 기본: dev)
 *
 * ### Loki 도구
 * - `fetch_errors`: 에러 로그 조회
 * - `query_logs`: 커스텀 LogQL 쿼리
 * - `test_connection`: Loki 연결 테스트
 * - `list_services`: 서비스 목록 조회
 * - `list_labels`: 라벨 목록 조회
 * - `get_error_summary`: 에러 요약
 *
 * ### Tempo 도구
 * - `get_trace`: 트레이스 조회
 * - `search_traces`: 트레이스 검색
 * - `test_tempo_connection`: Tempo 연결 테스트
 *
 * ### Template 도구
 * - `list_issue_templates`: 템플릿 목록
 * - `get_issue_template`: 템플릿 조회
 * - `import_github_template`: GitHub 템플릿 가져오기
 * - `set_default_template`: 기본 템플릿 설정
 * - `render_issue`: 이슈 렌더링
 */
fun main(): Unit = runBlocking {
    logger.info { "Starting $SERVER_NAME v$SERVER_VERSION..." }

    val config = loadConfig()
    logConfigurationSummary(config)

    // 환경별 어댑터 생성
    val adapters = mapOf(
        Environment.DEV to Adapters(
            loki = LokiAdapter(config.dev.loki),
            tempo = config.dev.tempo?.let { TempoAdapter(it) }
        ),
        Environment.PROD to Adapters(
            loki = LokiAdapter(config.prod.loki),
            tempo = config.prod.tempo?.let { TempoAdapter(it) }
        )
    )
    val templateManager = TemplateManager()

    val server = Server(
        Implementation(
            name = SERVER_NAME,
            version = SERVER_VERSION
        ),
        ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = true)
            )
        )
    )

    // 도구 등록
    ToolRegistry.registerAllTools(server, adapters, templateManager)

    // 서버 시작
    val transport = StdioServerTransport(
        inputStream = System.`in`.asSource().buffered(),
        outputStream = System.out.asSink().buffered()
    )

    server.createSession(transport)

    logger.info { "$SERVER_NAME started successfully" }

    // 서버가 닫힐 때까지 대기
    val done = Job()
    server.onClose {
        logger.info { "Server closing..." }
        adapters.values.forEach { adapter ->
            adapter.loki.close()
            adapter.tempo?.close()
        }
        done.complete()
    }
    done.join()

    logger.info { "Server shutdown complete" }
}

/**
 * 환경 변수에서 애플리케이션 설정을 로드합니다.
 *
 * 환경별(dev/prod) 설정을 지원합니다.
 *
 * @return 로드된 [AppConfig]
 */
private fun loadConfig(): AppConfig {
    val lokiOrgId = System.getenv("LOKI_ORG_ID") ?: LokiConfig.DEFAULT_ORG_ID
    val tempoOrgId = System.getenv("TEMPO_ORG_ID") ?: TempoConfig.DEFAULT_ORG_ID

    // 개발 환경 설정
    val lokiUrlDev = System.getenv("LOKI_URL_DEV") ?: "http://localhost:3100"
    val tempoUrlDev = System.getenv("TEMPO_URL_DEV") ?: "http://localhost:3200"

    // 운영 환경 설정
    val lokiUrlProd = System.getenv("LOKI_URL_PROD") ?: "http://localhost:3100"
    val tempoUrlProd = System.getenv("TEMPO_URL_PROD") ?: "http://localhost:3200"

    return AppConfig(
        dev = EnvironmentConfig(
            loki = LokiConfig(
                url = lokiUrlDev,
                orgId = lokiOrgId,
                environment = Environment.DEV
            ),
            tempo = TempoConfig(
                url = tempoUrlDev,
                orgId = tempoOrgId
            )
        ),
        prod = EnvironmentConfig(
            loki = LokiConfig(
                url = lokiUrlProd,
                orgId = lokiOrgId,
                environment = Environment.PROD
            ),
            tempo = TempoConfig(
                url = tempoUrlProd,
                orgId = tempoOrgId
            )
        ),
        github = System.getenv("GITHUB_REPO")?.let {
            GithubConfig(
                owner = System.getenv("GITHUB_OWNER") ?: "",
                repo = it
            )
        }
    )
}

/**
 * 설정 요약을 로깅합니다.
 */
private fun logConfigurationSummary(config: AppConfig) {
    logger.info { "Configuration:" }
    logger.info { "  [DEV Environment]" }
    logger.info { "    Loki URL: ${config.dev.loki.url}" }
    logger.info { "    Tempo URL: ${config.dev.tempo?.url ?: "Not configured"}" }
    logger.info { "  [PROD Environment]" }
    logger.info { "    Loki URL: ${config.prod.loki.url}" }
    logger.info { "    Tempo URL: ${config.prod.tempo?.url ?: "Not configured"}" }
    if (config.github != null) {
        logger.info { "  GitHub: ${config.github.fullName()}" }
    } else {
        logger.info { "  GitHub: Not configured" }
    }
}
