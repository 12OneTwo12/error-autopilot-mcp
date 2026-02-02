package io.github.onetwo.errorautopilot.adapter

import io.github.onetwo.errorautopilot.model.*
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*
import java.time.Instant

private val logger = KotlinLogging.logger {}

class TempoAdapter(private val config: TempoConfig) {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun testConnection(): Result<String> = runCatching {
        val response = client.get("${config.url}/ready")
        if (response.status.isSuccess()) {
            "Tempo 연결 성공 (${config.url})"
        } else {
            throw Exception("Tempo 연결 실패: ${response.status}")
        }
    }

    suspend fun getTrace(traceId: String): TraceData? {
        return try {
            val response = client.get("${config.url}/api/traces/$traceId") {
                header("X-Scope-OrgID", config.orgId)
            }

            if (!response.status.isSuccess()) {
                logger.warn { "Trace not found: $traceId" }
                return null
            }

            val jsonResponse: JsonObject = response.body()
            parseTraceResponse(traceId, jsonResponse)
        } catch (e: Exception) {
            logger.error(e) { "Failed to get trace: $traceId" }
            null
        }
    }

    suspend fun searchTraces(
        service: String? = null,
        sinceMinutes: Int = 60,
        minDuration: String? = null,
        maxDuration: String? = null,
        limit: Int = 20
    ): List<TraceData> {
        val now = Instant.now().epochSecond
        val start = now - (sinceMinutes * 60)

        val params = mutableListOf<String>()
        service?.let { params.add("service.name=$it") }
        minDuration?.let { params.add("minDuration=$it") }
        maxDuration?.let { params.add("maxDuration=$it") }

        val queryString = buildString {
            append("?start=$start&end=$now&limit=$limit")
            if (params.isNotEmpty()) {
                append("&tags=${params.joinToString("&")}")
            }
        }

        return try {
            val response = client.get("${config.url}/api/search$queryString") {
                header("X-Scope-OrgID", config.orgId)
            }

            val jsonResponse: JsonObject = response.body()
            val traces = jsonResponse["traces"]?.jsonArray ?: return emptyList()

            traces.mapNotNull { trace ->
                try {
                    parseSearchResult(trace.jsonObject)
                } catch (e: Exception) {
                    logger.warn(e) { "Failed to parse trace" }
                    null
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to search traces" }
            emptyList()
        }
    }

    private fun parseTraceResponse(traceId: String, response: JsonObject): TraceData? {
        val batches = response["batches"]?.jsonArray ?: return null

        val spans = mutableListOf<SpanData>()
        var rootService = "unknown"
        var rootOperation = "unknown"
        var startTimeNanos = Long.MAX_VALUE
        var endTimeNanos = 0L

        for (batch in batches) {
            val batchObj = batch.jsonObject
            val resource = batchObj["resource"]?.jsonObject
            val serviceName = resource?.get("attributes")?.jsonArray
                ?.find { it.jsonObject["key"]?.jsonPrimitive?.content == "service.name" }
                ?.jsonObject?.get("value")?.jsonObject?.get("stringValue")?.jsonPrimitive?.content
                ?: "unknown"

            val scopeSpans = batchObj["scopeSpans"]?.jsonArray ?: continue

            for (scopeSpan in scopeSpans) {
                val spanList = scopeSpan.jsonObject["spans"]?.jsonArray ?: continue

                for (spanJson in spanList) {
                    val span = spanJson.jsonObject
                    val spanStartTime = span["startTimeUnixNano"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0
                    val spanEndTime = span["endTimeUnixNano"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0

                    if (spanStartTime < startTimeNanos) {
                        startTimeNanos = spanStartTime
                        rootService = serviceName
                        rootOperation = span["name"]?.jsonPrimitive?.content ?: "unknown"
                    }
                    if (spanEndTime > endTimeNanos) {
                        endTimeNanos = spanEndTime
                    }

                    val attributes = span["attributes"]?.jsonArray?.associate {
                        val key = it.jsonObject["key"]?.jsonPrimitive?.content ?: ""
                        val value = it.jsonObject["value"]?.jsonObject?.values?.firstOrNull()
                            ?.jsonPrimitive?.content ?: ""
                        key to value
                    } ?: emptyMap()

                    val status = span["status"]?.jsonObject?.get("code")?.jsonPrimitive?.content ?: "ok"

                    spans.add(
                        SpanData(
                            spanId = span["spanId"]?.jsonPrimitive?.content ?: "",
                            serviceName = serviceName,
                            operationName = span["name"]?.jsonPrimitive?.content ?: "",
                            duration = (spanEndTime - spanStartTime) / 1_000_000.0,
                            status = if (status == "2") "error" else "ok",
                            attributes = attributes
                        )
                    )
                }
            }
        }

        if (spans.isEmpty()) return null

        return TraceData(
            traceId = traceId,
            rootService = rootService,
            rootOperation = rootOperation,
            startTime = Instant.ofEpochSecond(startTimeNanos / 1_000_000_000),
            duration = (endTimeNanos - startTimeNanos) / 1_000_000.0,
            spanCount = spans.size,
            spans = spans
        )
    }

    private fun parseSearchResult(trace: JsonObject): TraceData {
        val traceId = trace["traceID"]?.jsonPrimitive?.content ?: ""
        val rootServiceName = trace["rootServiceName"]?.jsonPrimitive?.content ?: "unknown"
        val rootTraceName = trace["rootTraceName"]?.jsonPrimitive?.content ?: "unknown"
        val startTimeUnixNano = trace["startTimeUnixNano"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0
        val durationMs = trace["durationMs"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0

        return TraceData(
            traceId = traceId,
            rootService = rootServiceName,
            rootOperation = rootTraceName,
            startTime = Instant.ofEpochSecond(startTimeUnixNano / 1_000_000_000),
            duration = durationMs,
            spanCount = 0,
            spans = emptyList()
        )
    }

    fun close() {
        client.close()
    }
}
