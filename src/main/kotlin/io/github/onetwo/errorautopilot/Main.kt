package io.github.onetwo.errorautopilot

import io.github.onetwo.errorautopilot.adapter.LokiAdapter
import io.github.onetwo.errorautopilot.adapter.TempoAdapter
import io.github.onetwo.errorautopilot.formatter.ErrorFormatter
import io.github.onetwo.errorautopilot.formatter.TraceFormatter
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

fun main(): Unit = runBlocking {
    val config = loadConfig()

    val loki = LokiAdapter(config.loki)
    val tempo = config.tempo?.let { TempoAdapter(it) }
    val templateManager = TemplateManager()

    val server = Server(
        Implementation(
            name = "error-autopilot-mcp",
            version = "1.0.0"
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

    logger.info { "Error Autopilot MCP Server started (Kotlin)" }

    // 서버가 닫힐 때까지 대기
    val done = Job()
    server.onClose {
        done.complete()
    }
    done.join()
}

private fun loadConfig(): AppConfig {
    val lokiUrl = System.getenv("LOKI_URL") ?: run {
        logger.warn { "LOKI_URL not set, using default" }
        "http://localhost:3100"
    }

    val tempoUrl = System.getenv("TEMPO_URL")

    return AppConfig(
        loki = LokiConfig(
            url = lokiUrl,
            orgId = System.getenv("LOKI_ORG_ID") ?: "default"
        ),
        tempo = tempoUrl?.let {
            TempoConfig(
                url = it,
                orgId = System.getenv("TEMPO_ORG_ID") ?: "default"
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
