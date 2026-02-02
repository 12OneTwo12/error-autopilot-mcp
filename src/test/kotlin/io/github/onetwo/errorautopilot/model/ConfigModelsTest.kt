package io.github.onetwo.errorautopilot.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 설정 모델들에 대한 단위 테스트.
 */
class ConfigModelsTest {

    // ==================== LokiConfig Tests ====================

    @Test
    fun `LokiConfig should accept valid http URL`() {
        val config = LokiConfig(url = "http://localhost:3100")
        assertEquals("http://localhost:3100", config.url)
    }

    @Test
    fun `LokiConfig should accept valid https URL`() {
        val config = LokiConfig(url = "https://loki.example.com")
        assertEquals("https://loki.example.com", config.url)
    }

    @Test
    fun `LokiConfig should throw for blank URL`() {
        assertFailsWith<IllegalArgumentException> {
            LokiConfig(url = "")
        }
        assertFailsWith<IllegalArgumentException> {
            LokiConfig(url = "   ")
        }
    }

    @Test
    fun `LokiConfig should throw for invalid URL scheme`() {
        assertFailsWith<IllegalArgumentException> {
            LokiConfig(url = "ftp://localhost:3100")
        }
        assertFailsWith<IllegalArgumentException> {
            LokiConfig(url = "localhost:3100")
        }
    }

    @Test
    fun `LokiConfig hasBasicAuth should check username and password`() {
        val withAuth = LokiConfig(
            url = "http://localhost:3100",
            username = "user",
            password = "pass"
        )
        val withoutAuth = LokiConfig(url = "http://localhost:3100")
        val partialAuth = LokiConfig(
            url = "http://localhost:3100",
            username = "user"
        )

        assertTrue(withAuth.hasBasicAuth())
        assertFalse(withoutAuth.hasBasicAuth())
        assertFalse(partialAuth.hasBasicAuth())
    }

    // ==================== TempoConfig Tests ====================

    @Test
    fun `TempoConfig should accept valid URL`() {
        val config = TempoConfig(url = "http://localhost:3200")
        assertEquals("http://localhost:3200", config.url)
    }

    @Test
    fun `TempoConfig should throw for invalid URL`() {
        assertFailsWith<IllegalArgumentException> {
            TempoConfig(url = "")
        }
        assertFailsWith<IllegalArgumentException> {
            TempoConfig(url = "invalid-url")
        }
    }

    // ==================== GithubConfig Tests ====================

    @Test
    fun `GithubConfig should accept valid values`() {
        val config = GithubConfig(owner = "12OneTwo12", repo = "upvy")
        assertEquals("12OneTwo12", config.owner)
        assertEquals("upvy", config.repo)
    }

    @Test
    fun `GithubConfig fullName should return owner slash repo`() {
        val config = GithubConfig(owner = "12OneTwo12", repo = "upvy")
        assertEquals("12OneTwo12/upvy", config.fullName())
    }

    @Test
    fun `GithubConfig should throw for blank owner`() {
        assertFailsWith<IllegalArgumentException> {
            GithubConfig(owner = "", repo = "upvy")
        }
    }

    @Test
    fun `GithubConfig should throw for blank repo`() {
        assertFailsWith<IllegalArgumentException> {
            GithubConfig(owner = "12OneTwo12", repo = "")
        }
    }

    // ==================== Environment Tests ====================

    @Test
    fun `Environment fromString should parse valid values`() {
        assertEquals(Environment.DEV, Environment.fromString("dev"))
        assertEquals(Environment.DEV, Environment.fromString("DEV"))
        assertEquals(Environment.PROD, Environment.fromString("prod"))
        assertEquals(Environment.PROD, Environment.fromString("production"))
        assertEquals(Environment.DEV, Environment.fromString(null))
        assertEquals(Environment.DEV, Environment.fromString("unknown"))
    }

    @Test
    fun `Environment should have correct labels and emoji`() {
        assertEquals("dev", Environment.DEV.label)
        assertEquals("🟢 개발", Environment.DEV.emoji)
        assertEquals("prod", Environment.PROD.label)
        assertEquals("🔴 운영", Environment.PROD.emoji)
    }

    // ==================== EnvironmentConfig Tests ====================

    @Test
    fun `EnvironmentConfig should hold loki and tempo config`() {
        val envConfig = EnvironmentConfig(
            loki = LokiConfig(url = "http://localhost:3100"),
            tempo = TempoConfig(url = "http://localhost:3200")
        )
        assertEquals("http://localhost:3100", envConfig.loki.url)
        assertEquals("http://localhost:3200", envConfig.tempo?.url)
    }

    // ==================== AppConfig Tests ====================

    @Test
    fun `AppConfig forEnvironment should return correct config`() {
        val devLoki = LokiConfig(url = "http://dev-loki:3100", environment = Environment.DEV)
        val prodLoki = LokiConfig(url = "http://prod-loki:3100", environment = Environment.PROD)

        val config = AppConfig(
            dev = EnvironmentConfig(loki = devLoki),
            prod = EnvironmentConfig(loki = prodLoki)
        )

        assertEquals("http://dev-loki:3100", config.forEnvironment(Environment.DEV).loki.url)
        assertEquals("http://prod-loki:3100", config.forEnvironment(Environment.PROD).loki.url)
    }

    @Test
    fun `AppConfig hasGithubIntegration should check github presence`() {
        val devConfig = EnvironmentConfig(loki = LokiConfig(url = "http://localhost:3100"))
        val prodConfig = EnvironmentConfig(loki = LokiConfig(url = "http://localhost:3100"))

        val withGithub = AppConfig(
            dev = devConfig,
            prod = prodConfig,
            github = GithubConfig(owner = "owner", repo = "repo")
        )
        val withoutGithub = AppConfig(
            dev = devConfig,
            prod = prodConfig
        )

        assertTrue(withGithub.hasGithubIntegration())
        assertFalse(withoutGithub.hasGithubIntegration())
    }
}
