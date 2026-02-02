package io.github.onetwo.errorautopilot

import io.github.onetwo.errorautopilot.adapter.LokiAdapter
import io.github.onetwo.errorautopilot.adapter.TempoAdapter
import io.github.onetwo.errorautopilot.model.FetchErrorsOptions
import io.github.onetwo.errorautopilot.model.LokiConfig
import io.github.onetwo.errorautopilot.model.TempoConfig
import kotlinx.coroutines.runBlocking

/**
 * 실제 서버 연결을 테스트하는 스크립트.
 *
 * 실행: ./gradlew run -PmainClass=io.github.onetwo.errorautopilot.TestConnectionKt
 */
fun main() = runBlocking {
    val lokiUrl = System.getenv("LOKI_URL") ?: "https://metrics.bootalk.co.kr"
    val tempoUrl = System.getenv("TEMPO_URL") ?: "https://metrics.bootalk.co.kr/tempo"

    println("=" .repeat(60))
    println("Error Autopilot MCP - Connection Test (Kotlin)")
    println("=" .repeat(60))
    println()

    // Loki 테스트
    println("📊 Testing Loki connection...")
    println("   URL: $lokiUrl")
    val loki = LokiAdapter(LokiConfig(url = lokiUrl))
    try {
        val lokiResult = loki.testConnection()
        lokiResult.fold(
            onSuccess = { println("   ✅ $it") },
            onFailure = { println("   ❌ Failed: ${it.message}") }
        )

        // 라벨 조회
        println()
        println("📋 Available labels:")
        val labels = loki.getLabels()
        labels.forEach { println("   - $it") }

        // 서비스 목록
        println()
        println("🔧 Available services:")
        val services = loki.getLabelValues("service_name")
        services.forEach { println("   - $it") }

        // 최근 에러 조회
        println()
        println("🔴 Recent errors (last 60 min, limit 5):")
        val errors = loki.fetchErrors(FetchErrorsOptions(sinceMinutes = 60, limit = 5))
        if (errors.isEmpty()) {
            println("   No errors found!")
        } else {
            errors.forEach { error ->
                println("   [${error.severity}] ${error.service ?: "unknown"}: ${error.title.take(50)}")
            }
        }
    } finally {
        loki.close()
    }

    println()
    println("-".repeat(60))
    println()

    // Tempo 테스트
    println("🔍 Testing Tempo connection...")
    println("   URL: $tempoUrl")
    val tempo = TempoAdapter(TempoConfig(url = tempoUrl))
    try {
        val tempoResult = tempo.testConnection()
        tempoResult.fold(
            onSuccess = { println("   ✅ $it") },
            onFailure = { println("   ❌ Failed: ${it.message}") }
        )

        // 최근 트레이스 검색
        println()
        println("📈 Recent traces (last 60 min, limit 3):")
        val traces = tempo.searchTraces(sinceMinutes = 60, limit = 3)
        if (traces.isEmpty()) {
            println("   No traces found!")
        } else {
            traces.forEach { trace ->
                println("   ${trace.rootService} / ${trace.rootOperation} (${trace.duration.toInt()}ms)")
            }
        }
    } finally {
        tempo.close()
    }

    println()
    println("=" .repeat(60))
    println("Test completed!")
    println("=" .repeat(60))
}
