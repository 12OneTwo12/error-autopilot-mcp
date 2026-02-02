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
 * Error Autopilot MCP 서버의 메인 진입점.
 *
 * 이 서버는 Loki/Tempo와 연동하여 에러 로그 분석 및 GitHub 이슈 생성을 자동화합니다.
 * MCP (Model Context Protocol)를 통해 Claude Code와 통합됩니다.
 *
 * ## 환경 변수
 * - `LOKI_URL`: Loki 서버 URL (필수, 기본: http://localhost:3100)
 * - `LOKI_ORG_ID`: Loki 멀티테넌시 조직 ID (선택, 기본: default)
 * - `TEMPO_URL`: Tempo 서버 URL (선택)
 * - `TEMPO_ORG_ID`: Tempo 멀티테넌시 조직 ID (선택, 기본: default)
 * - `GITHUB_REPO`: GitHub 리포지토리명 (선택)
 * - `GITHUB_OWNER`: GitHub 조직/사용자명 (선택)
 *
 * ## 제공하는 도구 (14개)
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

    val loki = LokiAdapter(config.loki)
    val tempo = config.tempo?.let { TempoAdapter(it) }
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
    ToolRegistry.registerAllTools(server, loki, tempo, templateManager)

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
        loki.close()
        tempo?.close()
        done.complete()
    }
    done.join()

    logger.info { "Server shutdown complete" }
}

/**
 * 환경 변수에서 애플리케이션 설정을 로드합니다.
 *
 * @return 로드된 [AppConfig]
 */
private fun loadConfig(): AppConfig {
    val lokiUrl = System.getenv("LOKI_URL") ?: run {
        logger.warn { "LOKI_URL not set, using default: http://localhost:3100" }
        "http://localhost:3100"
    }

    val tempoUrl = System.getenv("TEMPO_URL")

    return AppConfig(
        loki = LokiConfig(
            url = lokiUrl,
            orgId = System.getenv("LOKI_ORG_ID") ?: LokiConfig.DEFAULT_ORG_ID
        ),
        tempo = tempoUrl?.let {
            TempoConfig(
                url = it,
                orgId = System.getenv("TEMPO_ORG_ID") ?: TempoConfig.DEFAULT_ORG_ID
            )
        },
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
    logger.info { "  Loki URL: ${config.loki.url}" }
    logger.info { "  Loki Org ID: ${config.loki.orgId}" }
    if (config.tempo != null) {
        logger.info { "  Tempo URL: ${config.tempo.url}" }
        logger.info { "  Tempo Org ID: ${config.tempo.orgId}" }
    } else {
        logger.info { "  Tempo: Not configured" }
    }
    if (config.github != null) {
        logger.info { "  GitHub: ${config.github.fullName()}" }
    } else {
        logger.info { "  GitHub: Not configured" }
    }
}
