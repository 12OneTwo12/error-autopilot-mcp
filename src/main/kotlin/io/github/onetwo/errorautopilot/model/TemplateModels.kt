package io.github.onetwo.errorautopilot.model

import kotlinx.serialization.Serializable

@Serializable
data class IssueTemplate(
    val name: String,
    val titlePrefix: String,
    val labels: List<String>,
    val body: String
)

@Serializable
data class TemplateConfig(
    val defaultTemplate: String = "error_autopilot",
    val templates: MutableMap<String, IssueTemplate> = mutableMapOf()
)

data class RenderedIssue(
    val title: String,
    val body: String,
    val labels: List<String>
)
