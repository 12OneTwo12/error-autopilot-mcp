package io.github.onetwo.errorautopilot.model

import kotlinx.serialization.Serializable

@Serializable
data class LokiConfig(
    val url: String,
    val orgId: String = "default",
    val defaultQuery: String? = null,
    val username: String? = null,
    val password: String? = null
)

@Serializable
data class TempoConfig(
    val url: String,
    val orgId: String = "default",
    val username: String? = null,
    val password: String? = null
)

@Serializable
data class GithubConfig(
    val owner: String,
    val repo: String
)

data class AppConfig(
    val loki: LokiConfig,
    val tempo: TempoConfig? = null,
    val github: GithubConfig? = null
)
