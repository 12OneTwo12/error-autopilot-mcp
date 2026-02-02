package io.github.onetwo.errorautopilot.template

import io.github.onetwo.errorautopilot.model.IssueTemplate
import io.github.onetwo.errorautopilot.model.RenderedIssue
import io.github.onetwo.errorautopilot.model.TemplateConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * GitHub 이슈 템플릿을 관리하는 클래스.
 *
 * 템플릿의 CRUD 작업, GitHub에서 템플릿 가져오기, 템플릿 렌더링 등의 기능을 제공합니다.
 * 템플릿 설정은 JSON 파일로 영속화됩니다.
 *
 * @param configPath 설정 파일 경로 (기본: ~/.config/error-autopilot/templates.json)
 *
 * @sample
 * ```kotlin
 * val manager = TemplateManager()
 * val rendered = manager.renderTemplate(null, mapOf(
 *     "timestamp" to "2024-01-01 12:00:00",
 *     "service" to "api-server",
 *     "error_message" to "NullPointerException"
 * ))
 * ```
 */
class TemplateManager(
    configPath: String? = null
) {
    private val configFile: File = File(
        configPath ?: "${System.getProperty("user.home")}$DEFAULT_CONFIG_PATH"
    )
    private var config: TemplateConfig = loadConfig()

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    /**
     * 설정 파일에서 템플릿 설정을 로드합니다.
     *
     * 파일이 없거나 파싱에 실패하면 기본 템플릿으로 초기화합니다.
     */
    private fun loadConfig(): TemplateConfig {
        return try {
            if (configFile.exists()) {
                val content = configFile.readText()
                val loaded = json.decodeFromString<TemplateConfig>(content)
                // 기본 템플릿과 병합
                TemplateConfig(
                    defaultTemplate = loaded.defaultTemplate,
                    templates = (DEFAULT_TEMPLATES + loaded.templates).toMutableMap()
                )
            } else {
                TemplateConfig(
                    defaultTemplate = TemplateConfig.DEFAULT_TEMPLATE_ID,
                    templates = DEFAULT_TEMPLATES.toMutableMap()
                )
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to load template config from ${configFile.absolutePath}" }
            TemplateConfig(
                defaultTemplate = TemplateConfig.DEFAULT_TEMPLATE_ID,
                templates = DEFAULT_TEMPLATES.toMutableMap()
            )
        }
    }

    /**
     * 현재 설정을 파일에 저장합니다.
     */
    private fun saveConfig() {
        try {
            configFile.parentFile?.mkdirs()
            configFile.writeText(json.encodeToString(TemplateConfig.serializer(), config))
            logger.debug { "Template config saved to ${configFile.absolutePath}" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to save template config" }
        }
    }

    /**
     * 등록된 모든 템플릿 목록을 조회합니다.
     *
     * @return 템플릿 정보 목록 (ID, 이름, 기본 여부 포함)
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
     * ID로 특정 템플릿을 조회합니다.
     *
     * @param id 조회할 템플릿 ID
     * @return 템플릿 또는 찾지 못한 경우 null
     */
    fun getTemplate(id: String): IssueTemplate? {
        return config.templates[id]
    }

    /**
     * 현재 기본 템플릿을 조회합니다.
     *
     * @return 기본 템플릿 (항상 유효한 템플릿 반환 보장)
     */
    fun getDefaultTemplate(): IssueTemplate {
        return config.templates[config.defaultTemplate]
            ?: config.templates[TemplateConfig.DEFAULT_TEMPLATE_ID]
            ?: DEFAULT_TEMPLATES[TemplateConfig.DEFAULT_TEMPLATE_ID]!!
    }

    /**
     * 템플릿을 추가하거나 기존 템플릿을 수정합니다.
     *
     * @param id 템플릿 ID
     * @param template 저장할 템플릿
     */
    fun setTemplate(id: String, template: IssueTemplate) {
        config.templates[id] = template
        saveConfig()
        logger.info { "Template '$id' saved" }
    }

    /**
     * 기본 템플릿을 설정합니다.
     *
     * @param id 기본으로 설정할 템플릿 ID
     * @return 성공 여부 (존재하지 않는 ID면 false)
     */
    fun setDefaultTemplate(id: String): Boolean {
        if (!config.templates.containsKey(id)) {
            logger.warn { "Template '$id' not found, cannot set as default" }
            return false
        }
        config = config.copy(defaultTemplate = id)
        saveConfig()
        logger.info { "Default template set to '$id'" }
        return true
    }

    /**
     * 템플릿을 삭제합니다.
     *
     * 기본 내장 템플릿(error_autopilot)은 삭제할 수 없습니다.
     *
     * @param id 삭제할 템플릿 ID
     * @return 성공 여부
     */
    fun deleteTemplate(id: String): Boolean {
        if (!config.templates.containsKey(id)) {
            logger.warn { "Template '$id' not found" }
            return false
        }
        if (id == TemplateConfig.DEFAULT_TEMPLATE_ID) {
            logger.warn { "Cannot delete built-in template '$id'" }
            return false
        }
        config.templates.remove(id)
        if (config.defaultTemplate == id) {
            config = config.copy(defaultTemplate = TemplateConfig.DEFAULT_TEMPLATE_ID)
        }
        saveConfig()
        logger.info { "Template '$id' deleted" }
        return true
    }

    /**
     * GitHub 리포지토리의 이슈 템플릿을 가져와 등록합니다.
     *
     * YAML front matter 형식의 GitHub 이슈 템플릿을 파싱합니다.
     *
     * @param templateContent 템플릿 파일 내용 (YAML front matter 포함)
     * @param id 저장할 템플릿 ID
     * @return 파싱된 템플릿 또는 실패 시 null
     */
    fun importFromGitHub(templateContent: String, id: String): IssueTemplate? {
        return try {
            val template = parseGitHubTemplate(templateContent, id)
            if (template != null) {
                setTemplate(id, template)
            }
            template
        } catch (e: Exception) {
            logger.error(e) { "Failed to import GitHub template" }
            null
        }
    }

    /**
     * GitHub 템플릿 내용을 파싱합니다.
     */
    private fun parseGitHubTemplate(templateContent: String, id: String): IssueTemplate? {
        val frontMatterRegex = Regex("^---\\n([\\s\\S]*?)\\n---\\n([\\s\\S]*)$")
        val match = frontMatterRegex.find(templateContent) ?: return null

        val frontMatter = match.groupValues[1]
        val body = match.groupValues[2].trim()

        // 간단한 YAML 파싱
        val nameMatch = Regex("name:\\s*(.+)").find(frontMatter)
        val titleMatch = Regex("title:\\s*\"?([^\"\\n]+)\"?").find(frontMatter)
        val labelsMatch = Regex("labels:\\s*(.+)").find(frontMatter)

        return IssueTemplate(
            name = nameMatch?.groupValues?.get(1)?.trim() ?: id,
            titlePrefix = titleMatch?.groupValues?.get(1)?.trim() ?: "[BUG]",
            labels = labelsMatch?.groupValues?.get(1)?.split(",")?.map { it.trim() } ?: listOf("bug"),
            body = convertToTemplateFormat(body)
        )
    }

    /**
     * GitHub 템플릿의 플레이스홀더를 변수 형식으로 변환합니다.
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
     * 템플릿에 변수를 적용하여 최종 이슈 내용을 생성합니다.
     *
     * @param templateId 사용할 템플릿 ID (null이면 기본 템플릿 사용)
     * @param variables 치환할 변수 맵 (키: 변수명, 값: 치환값)
     * @return 렌더링된 이슈 내용
     *
     * @sample
     * ```kotlin
     * val rendered = manager.renderTemplate("error_autopilot", mapOf(
     *     "timestamp" to "2024-01-01 12:00:00",
     *     "service" to "api-server",
     *     "severity" to "ERROR",
     *     "error_message" to "Connection refused"
     * ))
     * ```
     */
    fun renderTemplate(
        templateId: String?,
        variables: Map<String, Any>
    ): RenderedIssue {
        val template = templateId?.let { getTemplate(it) } ?: getDefaultTemplate()
        var body = template.body

        // 변수 치환
        for ((key, value) in variables) {
            body = when (value) {
                is List<*> -> {
                    // 배열은 목록으로 변환
                    val listItems = value.joinToString("\n") { "- `$it`" }
                    body.replace(Regex("\\{\\{#each $key\\}}[\\s\\S]*?\\{\\{/each\\}}"), listItems)
                        .replace("{{$key}}", listItems)
                }
                else -> body.replace("{{$key}}", value.toString())
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

    /**
     * 템플릿 정보를 담는 데이터 클래스.
     *
     * @property id 템플릿 ID
     * @property name 템플릿 표시 이름
     * @property isDefault 기본 템플릿 여부
     */
    data class TemplateInfo(
        val id: String,
        val name: String,
        val isDefault: Boolean
    )

    companion object {
        /** 기본 설정 파일 경로 */
        private const val DEFAULT_CONFIG_PATH = "/.config/error-autopilot/templates.json"

        /** 기본 제공 템플릿 맵 */
        val DEFAULT_TEMPLATES = mapOf(
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
}
