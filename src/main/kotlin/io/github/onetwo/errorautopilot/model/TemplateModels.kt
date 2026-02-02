package io.github.onetwo.errorautopilot.model

import kotlinx.serialization.Serializable

/**
 * GitHub 이슈 템플릿을 정의하는 데이터 클래스.
 *
 * Error Autopilot에서 에러 분석 결과를 GitHub 이슈로 생성할 때 사용되는 템플릿입니다.
 * Mustache 스타일의 변수 치환을 지원합니다 (예: {{variable}}).
 *
 * @property name 템플릿 표시 이름 (예: "Bug Report", "Error Autopilot")
 * @property titlePrefix 이슈 제목에 붙는 접두사 (예: "[BUG]", "[ERROR]")
 * @property labels 이슈에 자동으로 추가할 라벨 목록
 * @property body 이슈 본문 템플릿 (Markdown 형식, 변수 플레이스홀더 포함)
 */
@Serializable
data class IssueTemplate(
    val name: String,
    val titlePrefix: String,
    val labels: List<String>,
    val body: String
) {
    init {
        require(name.isNotBlank()) { "Template name must not be blank" }
        require(titlePrefix.isNotBlank()) { "Title prefix must not be blank" }
    }

    /**
     * 템플릿 본문에서 변수 플레이스홀더 목록을 추출합니다.
     *
     * @return 발견된 변수 이름 목록 (예: ["timestamp", "service", "error_message"])
     */
    fun extractVariables(): List<String> {
        val regex = Regex("\\{\\{([\\w_]+)\\}\\}")
        return regex.findAll(body).map { it.groupValues[1] }.distinct().toList()
    }
}

/**
 * 템플릿 설정 파일의 구조를 정의하는 데이터 클래스.
 *
 * ~/.config/error-autopilot/templates.json 파일에 저장되는 설정입니다.
 *
 * @property defaultTemplate 기본으로 사용할 템플릿 ID
 * @property templates 등록된 템플릿 맵 (ID -> 템플릿)
 */
@Serializable
data class TemplateConfig(
    val defaultTemplate: String = DEFAULT_TEMPLATE_ID,
    val templates: MutableMap<String, IssueTemplate> = mutableMapOf()
) {
    /**
     * 기본 템플릿이 존재하는지 확인합니다.
     *
     * @return [defaultTemplate]에 해당하는 템플릿이 있으면 true
     */
    fun hasDefaultTemplate(): Boolean = templates.containsKey(defaultTemplate)

    /**
     * 템플릿 개수를 반환합니다.
     *
     * @return 등록된 템플릿 개수
     */
    fun templateCount(): Int = templates.size

    companion object {
        /** 기본 템플릿 ID */
        const val DEFAULT_TEMPLATE_ID = "error_autopilot"
    }
}

/**
 * 템플릿 렌더링 결과를 담는 데이터 클래스.
 *
 * 변수가 치환된 최종 이슈 내용을 포함합니다.
 *
 * @property title 완성된 이슈 제목
 * @property body 완성된 이슈 본문 (Markdown)
 * @property labels 적용할 라벨 목록
 */
data class RenderedIssue(
    val title: String,
    val body: String,
    val labels: List<String>
) {
    /**
     * GitHub CLI 명령어 형식으로 변환합니다.
     *
     * @param repo 대상 리포지토리 (owner/repo 형식)
     * @return gh issue create 명령어 문자열
     */
    fun toGhCommand(repo: String): String = buildString {
        append("gh issue create ")
        append("--repo $repo ")
        append("--title \"${title.replace("\"", "\\\"")}\" ")
        append("--label \"${labels.joinToString(",")}\" ")
        append("--body \"${body.replace("\"", "\\\"").replace("\n", "\\n")}\"")
    }
}
