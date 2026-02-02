package io.github.onetwo.errorautopilot.tool

import io.github.onetwo.errorautopilot.adapter.LokiAdapter
import io.github.onetwo.errorautopilot.adapter.TempoAdapter
import io.github.onetwo.errorautopilot.formatter.ErrorFormatter
import io.github.onetwo.errorautopilot.formatter.TraceFormatter
import io.github.onetwo.errorautopilot.model.FetchErrorsOptions
import io.github.onetwo.errorautopilot.model.Severity
import io.github.onetwo.errorautopilot.template.TemplateManager
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * MCP 도구 등록 담당 - 모든 도구를 서버에 등록
 */
object ToolRegistry {

    fun registerAllTools(
        server: Server,
        loki: LokiAdapter,
        tempo: TempoAdapter?,
        templateManager: TemplateManager
    ) {
        registerLokiTools(server, loki)
        registerTempoTools(server, tempo)
        registerTemplateTools(server, templateManager)
    }

    // ==================== Loki Tools ====================

    private fun registerLokiTools(server: Server, loki: LokiAdapter) {
        // 1. fetch_errors
        server.addTool(
            name = "fetch_errors",
            description = "Loki에서 에러 로그를 가져옵니다. severity, service, namespace로 필터링 가능합니다."
        ) { request ->
            val args = request.arguments ?: emptyMap()
            val sinceMinutes = (args["since_minutes"] as? Number)?.toInt() ?: 60
            val severityList = (args["severity"] as? List<*>)?.mapNotNull { s ->
                Severity.fromString(s.toString())
            } ?: listOf(Severity.ERROR, Severity.CRITICAL)
            val service = args["service"] as? String
            val namespace = args["namespace"] as? String
            val limit = (args["limit"] as? Number)?.toInt() ?: 100

            val options = FetchErrorsOptions(sinceMinutes, severityList, service, namespace, limit)
            val errors = loki.fetchErrors(options)

            if (errors.isEmpty()) {
                CallToolResult(content = listOf(TextContent("최근 ${sinceMinutes}분 이내에 발견된 에러가 없습니다.")))
            } else {
                val formatted = ErrorFormatter.format(errors)
                val summary = ErrorFormatter.generateSummary(errors)
                CallToolResult(content = listOf(TextContent("$summary\n\n---\n\n## 상세 내역\n\n$formatted")))
            }
        }

        // 2. query_logs
        server.addTool(
            name = "query_logs",
            description = "커스텀 LogQL 쿼리로 Loki 로그를 조회합니다."
        ) { request ->
            val args = request.arguments ?: emptyMap()
            val query = args["query"] as? String ?: throw IllegalArgumentException("query 필수")
            val sinceMinutes = (args["since_minutes"] as? Number)?.toInt() ?: 60
            val limit = (args["limit"] as? Number)?.toInt() ?: 100

            val errors = loki.query(query, sinceMinutes, limit)

            if (errors.isEmpty()) {
                CallToolResult(content = listOf(TextContent("쿼리 결과가 없습니다.\n쿼리: $query")))
            } else {
                val formatted = ErrorFormatter.format(errors)
                CallToolResult(content = listOf(TextContent("## 쿼리 결과 (${errors.size}개)\n\n쿼리: `$query`\n\n$formatted")))
            }
        }

        // 3. test_connection
        server.addTool(
            name = "test_connection",
            description = "Loki 서버 연결을 테스트합니다."
        ) { _ ->
            val result = loki.testConnection()
            val text = result.fold(
                onSuccess = { "✅ $it" },
                onFailure = { "❌ Loki 연결 실패: ${it.message}" }
            )
            CallToolResult(content = listOf(TextContent(text)))
        }

        // 4. list_services
        server.addTool(
            name = "list_services",
            description = "Loki에서 사용 가능한 서비스 목록을 조회합니다."
        ) { _ ->
            val services = try {
                loki.getLabelValues("service_name").ifEmpty {
                    loki.getLabelValues("service.name")
                }
            } catch (e: Exception) {
                emptyList()
            }

            val text = if (services.isNotEmpty()) {
                "## 사용 가능한 서비스\n\n${services.joinToString("\n") { "- $it" }}"
            } else {
                "등록된 서비스가 없습니다."
            }
            CallToolResult(content = listOf(TextContent(text)))
        }

        // 5. list_labels
        server.addTool(
            name = "list_labels",
            description = "Loki에서 사용 가능한 모든 레이블 목록을 조회합니다."
        ) { _ ->
            val labels = loki.getLabels()
            CallToolResult(content = listOf(TextContent("## 사용 가능한 레이블\n\n${labels.joinToString("\n") { "- $it" }}")))
        }

        // 6. get_error_summary
        server.addTool(
            name = "get_error_summary",
            description = "에러 로그의 요약 정보를 반환합니다. 서비스별, 심각도별 개수를 포함합니다."
        ) { request ->
            val args = request.arguments ?: emptyMap()
            val sinceMinutes = (args["since_minutes"] as? Number)?.toInt() ?: 60
            val errors = loki.fetchErrors(FetchErrorsOptions(
                sinceMinutes = sinceMinutes,
                severity = listOf(Severity.CRITICAL, Severity.ERROR, Severity.WARNING),
                limit = 1000
            ))
            val summary = ErrorFormatter.generateSummary(errors)
            CallToolResult(content = listOf(TextContent(summary)))
        }
    }

    // ==================== Tempo Tools ====================

    private fun registerTempoTools(server: Server, tempo: TempoAdapter?) {
        // 7. get_trace
        server.addTool(
            name = "get_trace",
            description = "trace_id로 분산 트레이스를 조회합니다. 에러 로그에서 발견된 trace_id를 사용하여 전체 요청 흐름을 확인할 수 있습니다."
        ) { request ->
            if (tempo == null) {
                return@addTool CallToolResult(
                    content = listOf(TextContent("Tempo가 설정되지 않았습니다. TEMPO_URL 환경변수를 확인하세요.")),
                    isError = true
                )
            }

            val args = request.arguments ?: emptyMap()
            val traceId = args["trace_id"] as? String ?: throw IllegalArgumentException("trace_id 필수")
            val trace = tempo.getTrace(traceId)

            if (trace == null) {
                CallToolResult(content = listOf(TextContent("트레이스를 찾을 수 없습니다: $traceId")))
            } else {
                CallToolResult(content = listOf(TextContent(TraceFormatter.format(trace))))
            }
        }

        // 8. search_traces
        server.addTool(
            name = "search_traces",
            description = "서비스, 시간 범위 등으로 트레이스를 검색합니다. 느린 요청이나 에러가 발생한 트레이스를 찾을 수 있습니다."
        ) { request ->
            if (tempo == null) {
                return@addTool CallToolResult(
                    content = listOf(TextContent("Tempo가 설정되지 않았습니다. TEMPO_URL 환경변수를 확인하세요.")),
                    isError = true
                )
            }

            val args = request.arguments ?: emptyMap()
            val traces = tempo.searchTraces(
                service = args["service"] as? String,
                sinceMinutes = (args["since_minutes"] as? Number)?.toInt() ?: 60,
                minDuration = args["min_duration"] as? String,
                maxDuration = args["max_duration"] as? String,
                limit = (args["limit"] as? Number)?.toInt() ?: 20
            )
            CallToolResult(content = listOf(TextContent(TraceFormatter.formatList(traces))))
        }

        // 9. test_tempo_connection
        server.addTool(
            name = "test_tempo_connection",
            description = "Tempo 서버 연결을 테스트합니다."
        ) { _ ->
            if (tempo == null) {
                return@addTool CallToolResult(
                    content = listOf(TextContent("❌ Tempo가 설정되지 않았습니다. TEMPO_URL 환경변수를 확인하세요.")),
                    isError = true
                )
            }

            val result = tempo.testConnection()
            val text = result.fold(
                onSuccess = { "✅ $it" },
                onFailure = { "❌ Tempo 연결 실패: ${it.message}" }
            )
            CallToolResult(content = listOf(TextContent(text)))
        }
    }

    // ==================== Template Tools ====================

    private fun registerTemplateTools(server: Server, templateManager: TemplateManager) {
        // 10. list_issue_templates
        server.addTool(
            name = "list_issue_templates",
            description = "등록된 이슈 템플릿 목록을 조회합니다."
        ) { _ ->
            val templates = templateManager.listTemplates()
            val lines = buildString {
                appendLine("## 등록된 이슈 템플릿")
                appendLine()
                appendLine("| ID | 이름 | 기본 |")
                appendLine("|-----|------|------|")
                templates.forEach { t ->
                    appendLine("| ${t.id} | ${t.name} | ${if (t.isDefault) "✅" else ""} |")
                }
            }
            CallToolResult(content = listOf(TextContent(lines)))
        }

        // 11. get_issue_template
        server.addTool(
            name = "get_issue_template",
            description = "특정 이슈 템플릿의 상세 내용을 조회합니다."
        ) { request ->
            val args = request.arguments ?: emptyMap()
            val templateId = args["template_id"] as? String ?: throw IllegalArgumentException("template_id 필수")
            val template = templateManager.getTemplate(templateId)

            if (template == null) {
                CallToolResult(
                    content = listOf(TextContent("❌ 템플릿을 찾을 수 없습니다: $templateId")),
                    isError = true
                )
            } else {
                val text = buildString {
                    appendLine("## 템플릿: ${template.name}")
                    appendLine()
                    appendLine("- **Title Prefix**: ${template.titlePrefix}")
                    appendLine("- **Labels**: ${template.labels.joinToString(", ")}")
                    appendLine()
                    appendLine("### Body Template")
                    appendLine("```markdown")
                    appendLine(template.body)
                    appendLine("```")
                }
                CallToolResult(content = listOf(TextContent(text)))
            }
        }

        // 12. import_github_template
        server.addTool(
            name = "import_github_template",
            description = "GitHub 리포지토리의 이슈 템플릿을 가져와 등록합니다."
        ) { request ->
            val args = request.arguments ?: emptyMap()
            val repo = args["repo"] as? String ?: throw IllegalArgumentException("repo 필수")
            val templateFile = args["template_file"] as? String ?: throw IllegalArgumentException("template_file 필수")
            val templateId = args["template_id"] as? String ?: throw IllegalArgumentException("template_id 필수")

            try {
                // GitHub CLI로 템플릿 가져오기
                val process = ProcessBuilder(
                    "sh", "-c",
                    "gh api repos/$repo/contents/.github/ISSUE_TEMPLATE/$templateFile --jq '.content' | base64 -d"
                ).start()

                val content = process.inputStream.bufferedReader().readText()
                val exitCode = process.waitFor()

                if (exitCode != 0 || content.isBlank()) {
                    return@addTool CallToolResult(
                        content = listOf(TextContent("❌ GitHub에서 템플릿을 가져오는데 실패했습니다.")),
                        isError = true
                    )
                }

                val template = templateManager.importFromGitHub(content, templateId)

                if (template == null) {
                    CallToolResult(
                        content = listOf(TextContent("❌ 템플릿 파싱에 실패했습니다.")),
                        isError = true
                    )
                } else {
                    CallToolResult(content = listOf(TextContent(
                        "✅ 템플릿 가져오기 성공!\n\n- ID: $templateId\n- 이름: ${template.name}\n- Labels: ${template.labels.joinToString(", ")}"
                    )))
                }
            } catch (e: Exception) {
                CallToolResult(
                    content = listOf(TextContent("❌ GitHub에서 템플릿을 가져오는데 실패했습니다: ${e.message}")),
                    isError = true
                )
            }
        }

        // 13. set_default_template
        server.addTool(
            name = "set_default_template",
            description = "기본 이슈 템플릿을 설정합니다."
        ) { request ->
            val args = request.arguments ?: emptyMap()
            val templateId = args["template_id"] as? String ?: throw IllegalArgumentException("template_id 필수")
            val success = templateManager.setDefaultTemplate(templateId)

            if (success) {
                CallToolResult(content = listOf(TextContent("✅ 기본 템플릿이 '$templateId'로 설정되었습니다.")))
            } else {
                CallToolResult(
                    content = listOf(TextContent("❌ 템플릿을 찾을 수 없습니다: $templateId")),
                    isError = true
                )
            }
        }

        // 14. render_issue
        server.addTool(
            name = "render_issue",
            description = "템플릿과 변수를 사용하여 이슈 내용을 생성합니다."
        ) { request ->
            val args = request.arguments ?: emptyMap()
            val templateId = args["template_id"] as? String
            @Suppress("UNCHECKED_CAST")
            val variables = (args["variables"] as? Map<String, Any>) ?: emptyMap()

            val rendered = templateManager.renderTemplate(templateId, variables)

            val text = buildString {
                appendLine("## 렌더링된 이슈")
                appendLine()
                appendLine("**Title**: ${rendered.title}")
                appendLine("**Labels**: ${rendered.labels.joinToString(", ")}")
                appendLine()
                appendLine("### Body")
                appendLine("```markdown")
                appendLine(rendered.body)
                appendLine("```")
            }
            CallToolResult(content = listOf(TextContent(text)))
        }
    }
}
