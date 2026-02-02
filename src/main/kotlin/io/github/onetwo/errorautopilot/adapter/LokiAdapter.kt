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
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import java.time.Instant

private val logger = KotlinLogging.logger {}

class LokiAdapter(private val config: LokiConfig) {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    suspend fun testConnection(): Result<String> = runCatching {
        val response = client.get("${config.url}/ready")
        if (response.status.isSuccess()) {
            "Loki 연결 성공 (${config.url})"
        } else {
            throw Exception("Loki 연결 실패: ${response.status}")
        }
    }

    suspend fun getLabels(): List<String> {
        val response = client.get("${config.url}/loki/api/v1/labels") {
            header("X-Scope-OrgID", config.orgId)
        }
        val result: LokiLabelsResponse = response.body()
        return result.data
    }

    suspend fun getLabelValues(label: String): List<String> {
        val response = client.get("${config.url}/loki/api/v1/label/$label/values") {
            header("X-Scope-OrgID", config.orgId)
        }
        val result: LokiLabelsResponse = response.body()
        return result.data
    }

    suspend fun fetchErrors(options: FetchErrorsOptions): List<UnifiedError> {
        val query = buildQuery(options)
        return query(query, options.sinceMinutes, options.limit)
    }

    suspend fun query(logQL: String, sinceMinutes: Int, limit: Int): List<UnifiedError> {
        val now = Instant.now()
        val start = now.minusSeconds(sinceMinutes * 60L)

        val encodedQuery = URLEncoder.encode(logQL, "UTF-8")
        val url = "${config.url}/loki/api/v1/query_range" +
                "?query=$encodedQuery" +
                "&start=${start.epochSecond}" +
                "&end=${now.epochSecond}" +
                "&limit=$limit"

        logger.debug { "Loki query: $logQL" }

        val response = client.get(url) {
            header("X-Scope-OrgID", config.orgId)
        }

        val result: LokiQueryResponse = response.body()

        return result.data.result.flatMap { stream ->
            stream.values.map { value ->
                parseLogEntry(stream.stream, value)
            }
        }.sortedByDescending { it.timestamp }
    }

    private fun buildQuery(options: FetchErrorsOptions): String {
        val selectors = mutableListOf<String>()

        if (options.service != null) {
            selectors.add("service_name=\"${options.service}\"")
        } else {
            selectors.add("service_name=~\".+\"")
        }

        if (options.namespace != null) {
            selectors.add("namespace=\"${options.namespace}\"")
        }

        val selector = selectors.joinToString(", ", "{", "}")

        // severity filter
        val severityPatterns = options.severity.map { it.name.lowercase() }
        val severityFilter = if (severityPatterns.isNotEmpty()) {
            severityPatterns.joinToString("|", " |~ \"(?i)(", ")\"")
        } else ""

        return "$selector$severityFilter"
    }

    private fun parseLogEntry(stream: Map<String, String>, value: List<String>): UnifiedError {
        val timestampNanos = value[0].toLong()
        val message = value.getOrElse(1) { "" }

        val severity = detectSeverity(message, stream)
        val title = extractTitle(message)

        return UnifiedError(
            timestamp = Instant.ofEpochSecond(timestampNanos / 1_000_000_000, timestampNanos % 1_000_000_000),
            severity = severity,
            title = title,
            message = message,
            service = stream["service_name"] ?: stream["service.name"],
            namespace = stream["namespace"],
            pod = stream["pod"] ?: stream["k8s.pod.name"],
            traceId = extractTraceId(message, stream),
            labels = stream
        )
    }

    private fun detectSeverity(message: String, labels: Map<String, String>): Severity {
        val levelLabel = labels["level"] ?: labels["severity"] ?: ""
        val combined = "$levelLabel $message".lowercase()

        return when {
            "critical" in combined || "fatal" in combined -> Severity.CRITICAL
            "error" in combined || "exception" in combined -> Severity.ERROR
            "warn" in combined -> Severity.WARNING
            else -> Severity.INFO
        }
    }

    private fun extractTitle(message: String): String {
        // Extract first line or first 100 chars
        val firstLine = message.lines().firstOrNull() ?: message
        return if (firstLine.length > 100) firstLine.take(100) + "..." else firstLine
    }

    private fun extractTraceId(message: String, labels: Map<String, String>): String? {
        // Check labels first
        labels["trace_id"]?.let { return it }
        labels["traceId"]?.let { return it }

        // Try to extract from message
        val traceIdPattern = Regex("[0-9a-f]{32}|[0-9a-f-]{36}")
        return traceIdPattern.find(message)?.value
    }

    fun close() {
        client.close()
    }
}
