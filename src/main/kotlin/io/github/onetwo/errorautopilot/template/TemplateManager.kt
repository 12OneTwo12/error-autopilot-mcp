package io.github.onetwo.errorautopilot.template

import io.github.onetwo.errorautopilot.model.IssueTemplate
import io.github.onetwo.errorautopilot.model.RenderedIssue
import io.github.onetwo.errorautopilot.model.TemplateConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import java.io.File

private val logger = KotlinLogging.logger {}

class TemplateManager(
    configPath: String? = null
) {
    private val configFile: File = File(
        configPath ?: "${System.getProperty("user.home")}/.config/error-autopilot/templates.json"
    )
    private var config: TemplateConfig = loadConfig()

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    companion object {
        private val DEFAULT_TEMPLATES = mapOf(
            "bug_report" to IssueTemplate(
                name = "Bug report",
                titlePrefix = "[BUG]",
                labels = listOf("bug"),
                body = """### 문제 사항 요약 및 배경
{{error_summary}}

---

### 버그 상세

#### 버그 상황
{{error_detail}}

#### 재현 방법
{{reproduction}}

#### 기대 동작
{{expected_behavior}}

#### 버그 발생환경
{{environment}}

#### 스크린샷
{{screenshots}}

---

### 참고 사항
{{notes}}

---

### 관련 문서
{{related_docs}}

---
_이 이슈는 Error Autopilot에 의해 자동 생성되었습니다._"""
            ),
            "error_autopilot" to IssueTemplate(
                name = "Error Autopilot",
                titlePrefix = "[ERROR]",
                labels = listOf("bug", "auto-generated"),
                body = """## 🔴 에러 요약
- **발생 시간**: {{timestamp}}
- **서비스**: {{service}}
- **심각도**: {{severity}}
- **Pod**: {{pod}}

## 📋 에러 상세
```
{{error_message}}
```

{{#if stack_trace}}
<details>
<summary>스택 트레이스</summary>

```
{{stack_trace}}
```
</details>
{{/if}}

## 🔍 근본 원인 분석
{{root_cause}}

## 📁 영향받는 파일
{{#each affected_files}}
- `{{this}}`
{{/each}}

## 💡 제안된 수정
{{suggested_fix}}

## 🔗 관련 정보
- Trace ID: `{{trace_id}}`
- Grafana: [View Logs]({{grafana_url}})

---
_이 이슈는 Error Autopilot에 의해 자동 생성되었습니다._"""
            )
        )
    }

    private fun loadConfig(): TemplateConfig {
        return try {
            if (configFile.exists()) {
                val content = configFile.readText()
                val loaded = json.decodeFromString<TemplateConfig>(content)
                // Merge with defaults
                TemplateConfig(
                    defaultTemplate = loaded.defaultTemplate,
                    templates = (DEFAULT_TEMPLATES + loaded.templates).toMutableMap()
                )
            } else {
                TemplateConfig(
                    defaultTemplate = "error_autopilot",
                    templates = DEFAULT_TEMPLATES.toMutableMap()
                )
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to load template config" }
            TemplateConfig(
                defaultTemplate = "error_autopilot",
                templates = DEFAULT_TEMPLATES.toMutableMap()
            )
        }
    }

    private fun saveConfig() {
        try {
            configFile.parentFile?.mkdirs()
            configFile.writeText(json.encodeToString(TemplateConfig.serializer(), config))
        } catch (e: Exception) {
            logger.error(e) { "Failed to save template config" }
        }
    }

    /**
     * 모든 템플릿 목록 조회
     */
    fun listTemplates(): List<TemplateInfo> {
        return config.templates.map { (id, template) ->
            TemplateInfo(
                id = id,
                name = template.name,
                isDefault = id == config.defaultTemplate
            )
        }
    }

    /**
     * 특정 템플릿 조회
     */
    fun getTemplate(id: String): IssueTemplate? {
        return config.templates[id]
    }

    /**
     * 기본 템플릿 조회
     */
    fun getDefaultTemplate(): IssueTemplate {
        return config.templates[config.defaultTemplate]
            ?: config.templates["error_autopilot"]
            ?: DEFAULT_TEMPLATES["error_autopilot"]!!
    }

    /**
     * 템플릿 추가/수정
     */
    fun setTemplate(id: String, template: IssueTemplate) {
        config.templates[id] = template
        saveConfig()
    }

    /**
     * 기본 템플릿 설정
     */
    fun setDefaultTemplate(id: String): Boolean {
        if (!config.templates.containsKey(id)) {
            return false
        }
        config = config.copy(defaultTemplate = id)
        saveConfig()
        return true
    }

    /**
     * 템플릿 삭제
     */
    fun deleteTemplate(id: String): Boolean {
        if (!config.templates.containsKey(id) || id == "error_autopilot") {
            return false // 기본 템플릿은 삭제 불가
        }
        config.templates.remove(id)
        if (config.defaultTemplate == id) {
            config = config.copy(defaultTemplate = "error_autopilot")
        }
        saveConfig()
        return true
    }

    /**
     * GitHub 이슈 템플릿에서 가져오기
     */
    fun importFromGitHub(templateContent: String, id: String): IssueTemplate? {
        return try {
            // YAML front matter 파싱
            val frontMatterRegex = Regex("^---\\n([\\s\\S]*?)\\n---\\n([\\s\\S]*)$")
            val match = frontMatterRegex.find(templateContent) ?: return null

            val frontMatter = match.groupValues[1]
            val body = match.groupValues[2].trim()

            // 간단한 YAML 파싱
            val nameMatch = Regex("name:\\s*(.+)").find(frontMatter)
            val titleMatch = Regex("title:\\s*\"?([^\"\\n]+)\"?").find(frontMatter)
            val labelsMatch = Regex("labels:\\s*(.+)").find(frontMatter)

            val template = IssueTemplate(
                name = nameMatch?.groupValues?.get(1)?.trim() ?: id,
                titlePrefix = titleMatch?.groupValues?.get(1)?.trim() ?: "[BUG]",
                labels = labelsMatch?.groupValues?.get(1)?.split(",")?.map { it.trim() } ?: listOf("bug"),
                body = convertToTemplateFormat(body)
            )

            setTemplate(id, template)
            template
        } catch (e: Exception) {
            logger.error(e) { "Failed to import GitHub template" }
            null
        }
    }

    /**
     * GitHub 템플릿 형식을 변수 형식으로 변환
     */
    private fun convertToTemplateFormat(body: String): String {
        return body
            .replace("[버그에 대한 간단한 설명을 적어주세요]", "{{error_summary}}")
            .replace("[버그 상황에 대해 적어주세요]", "{{error_detail}}")
            .replace("[버그 재현 방법에 대해 적어주세요]", "{{reproduction}}")
            .replace("[원래 기대했던 동작에 대해 설명해주세요]", "{{expected_behavior}}")
            .replace(Regex("\\[버그 발생환경에 대해 적어주세요.*?\\]"), "{{environment}}")
            .replace("[가능한 경우 문제를 설명하는 데 도움이 되는 스크린샷을 첨부해주세요]", "{{screenshots}}")
            .replace("[참고사항이 존재하면 적어주세요]", "{{notes}}")
            .replace(Regex("\\[관련 문서가 있다면 적어주세요.*?\\]"), "{{related_docs}}")
    }

    /**
     * 템플릿 렌더링
     */
    fun renderTemplate(
        templateId: String?,
        variables: Map<String, Any>
    ): RenderedIssue {
        val template = templateId?.let { getTemplate(it) } ?: getDefaultTemplate()
        var body = template.body

        // 변수 치환
        for ((key, value) in variables) {
            when (value) {
                is List<*> -> {
                    // 배열은 목록으로 변환
                    val listItems = value.joinToString("\n") { "- `$it`" }
                    body = body.replace(Regex("\\{\\{#each $key\\}}[\\s\\S]*?\\{\\{/each\\}}"), listItems)
                    body = body.replace("{{$key}}", listItems)
                }
                else -> {
                    body = body.replace("{{$key}}", value.toString())
                }
            }
        }

        // 조건부 블록 처리
        body = body.replace(Regex("\\{\\{#if (\\w+)\\}\\}([\\s\\S]*?)\\{\\{/if\\}\\}")) { matchResult ->
            val key = matchResult.groupValues[1]
            val content = matchResult.groupValues[2]
            if (variables.containsKey(key) && variables[key] != null) content else ""
        }

        // 미사용 변수 정리
        body = body.replace(Regex("\\{\\{[\\w#/]+\\}\\}"), "N/A")

        val service = variables["service"]?.toString() ?: ""
        val errorTitle = variables["error_title"]?.toString() ?: "Error"

        return RenderedIssue(
            title = "${template.titlePrefix} $service: $errorTitle".trim(),
            body = body,
            labels = template.labels
        )
    }

    data class TemplateInfo(
        val id: String,
        val name: String,
        val isDefault: Boolean
    )
}
