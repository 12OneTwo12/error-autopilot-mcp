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

    // ==================== AppConfig Tests ====================

    @Test
    fun `AppConfig hasTempoIntegration should check tempo presence`() {
        val withTempo = AppConfig(
            loki = LokiConfig(url = "http://localhost:3100"),
            tempo = TempoConfig(url = "http://localhost:3200")
        )
        val withoutTempo = AppConfig(
            loki = LokiConfig(url = "http://localhost:3100")
        )

        assertTrue(withTempo.hasTempoIntegration())
        assertFalse(withoutTempo.hasTempoIntegration())
    }

    @Test
    fun `AppConfig hasGithubIntegration should check github presence`() {
        val withGithub = AppConfig(
            loki = LokiConfig(url = "http://localhost:3100"),
            github = GithubConfig(owner = "owner", repo = "repo")
        )
        val withoutGithub = AppConfig(
            loki = LokiConfig(url = "http://localhost:3100")
        )

        assertTrue(withGithub.hasGithubIntegration())
        assertFalse(withoutGithub.hasGithubIntegration())
    }
}
