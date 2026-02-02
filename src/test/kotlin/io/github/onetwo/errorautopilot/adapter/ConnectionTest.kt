package io.github.onetwo.errorautopilot.adapter

import io.github.onetwo.errorautopilot.model.FetchErrorsOptions
import io.github.onetwo.errorautopilot.model.LokiConfig
import io.github.onetwo.errorautopilot.model.TempoConfig
import kotlinx.coroutines.runBlocking
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 실제 서버 연결 테스트 (수동 실행용).
 *
 * 이 테스트는 실제 Loki/Tempo 서버에 연결하므로 기본적으로 @Ignore 처리됨.
 * 로컬에서 수동으로 테스트할 때 @Ignore를 제거하고 실행.
 */
class ConnectionTest {

    private val lokiConfig = LokiConfig(
        url = System.getenv("LOKI_URL") ?: "https://metrics.bootalk.co.kr",
        orgId = System.getenv("LOKI_ORG_ID") ?: "default"
    )

    private val tempoConfig = TempoConfig(
        url = System.getenv("TEMPO_URL") ?: "https://metrics.bootalk.co.kr/tempo",
        orgId = System.getenv("TEMPO_ORG_ID") ?: "default"
    )

    @Test
    @Ignore("Manual test - requires real server connection")
    fun `test Loki connection`() = runBlocking {
        val adapter = LokiAdapter(lokiConfig)
        try {
            val result = adapter.testConnection()
            println("Loki connection result: $result")
            assertTrue(result.isSuccess, "Loki connection should succeed")
        } finally {
            adapter.close()
        }
    }

    @Test
    @Ignore("Manual test - requires real server connection")
    fun `test Tempo connection`() = runBlocking {
        val adapter = TempoAdapter(tempoConfig)
        try {
            val result = adapter.testConnection()
            println("Tempo connection result: $result")
            assertTrue(result.isSuccess, "Tempo connection should succeed")
        } finally {
            adapter.close()
        }
    }

    @Test
    @Ignore("Manual test - requires real server connection")
    fun `test Loki getLabels`() = runBlocking {
        val adapter = LokiAdapter(lokiConfig)
        try {
            val labels = adapter.getLabels()
            println("Available labels: $labels")
            assertTrue(labels.isNotEmpty(), "Should have labels")
        } finally {
            adapter.close()
        }
    }

    @Test
    @Ignore("Manual test - requires real server connection")
    fun `test Loki list services`() = runBlocking {
        val adapter = LokiAdapter(lokiConfig)
        try {
            val services = adapter.getLabelValues("service_name")
            println("Available services: $services")
            assertTrue(services.isNotEmpty(), "Should have services")
        } finally {
            adapter.close()
        }
    }

    @Test
    @Ignore("Manual test - requires real server connection")
    fun `test Loki fetch errors`() = runBlocking {
        val adapter = LokiAdapter(lokiConfig)
        try {
            val errors = adapter.fetchErrors(FetchErrorsOptions(
                sinceMinutes = 60,
                limit = 10
            ))
            println("Found ${errors.size} errors in last hour")
            errors.take(3).forEach {
                println("  - [${it.severity}] ${it.service}: ${it.title}")
            }
        } finally {
            adapter.close()
        }
    }
}
