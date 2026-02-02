package io.github.onetwo.errorautopilot.model

import kotlinx.serialization.Serializable

@Serializable
data class LokiQueryResponse(
    val status: String,
    val data: LokiData
)

@Serializable
data class LokiData(
    val resultType: String,
    val result: List<LokiStream>
)

@Serializable
data class LokiStream(
    val stream: Map<String, String>,
    val values: List<List<String>>
)

@Serializable
data class LokiLabelsResponse(
    val status: String,
    val data: List<String>
)
