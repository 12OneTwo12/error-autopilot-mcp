package io.github.onetwo.errorautopilot.template

import io.github.onetwo.errorautopilot.model.IssueTemplate
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [TemplateManager]에 대한 단위 테스트.
 */
class TemplateManagerTest {

    private lateinit var tempConfigFile: File
    private lateinit var manager: TemplateManager

    @BeforeTest
    fun setup() {
        tempConfigFile = File.createTempFile("templates", ".json")
        tempConfigFile.deleteOnExit()
        manager = TemplateManager(tempConfigFile.absolutePath)
    }

    @AfterTest
    fun teardown() {
        tempConfigFile.delete()
    }

    @Test
    fun `listTemplates should include default templates`() {
        val templates = manager.listTemplates()

        assertTrue(templates.any { it.id == "error_autopilot" })
        assertTrue(templates.any { it.id == "bug_report" })
    }

    @Test
    fun `getTemplate should return existing template`() {
        val template = manager.getTemplate("error_autopilot")

        assertNotNull(template)
        assertEquals("Error Autopilot", template.name)
        assertEquals("[ERROR]", template.titlePrefix)
    }

    @Test
    fun `getTemplate should return null for non-existing template`() {
        val template = manager.getTemplate("non-existing")

        assertNull(template)
    }

    @Test
    fun `getDefaultTemplate should return error_autopilot by default`() {
        val template = manager.getDefaultTemplate()

        assertEquals("Error Autopilot", template.name)
    }

    @Test
    fun `setTemplate should add new template`() {
        val newTemplate = IssueTemplate(
            name = "Custom Template",
            titlePrefix = "[CUSTOM]",
            labels = listOf("custom"),
            body = "Custom body"
        )

        manager.setTemplate("custom", newTemplate)
        val retrieved = manager.getTemplate("custom")

        assertNotNull(retrieved)
        assertEquals("Custom Template", retrieved.name)
    }

    @Test
    fun `setDefaultTemplate should change default`() {
        val success = manager.setDefaultTemplate("bug_report")

        assertTrue(success)
        val templates = manager.listTemplates()
        assertTrue(templates.first { it.id == "bug_report" }.isDefault)
    }

    @Test
    fun `setDefaultTemplate should return false for non-existing template`() {
        val success = manager.setDefaultTemplate("non-existing")

        assertFalse(success)
    }

    @Test
    fun `deleteTemplate should not allow deleting error_autopilot`() {
        val success = manager.deleteTemplate("error_autopilot")

        assertFalse(success)
        assertNotNull(manager.getTemplate("error_autopilot"))
    }

    @Test
    fun `deleteTemplate should remove custom template`() {
        val newTemplate = IssueTemplate(
            name = "To Delete",
            titlePrefix = "[DELETE]",
            labels = listOf(),
            body = ""
        )
        manager.setTemplate("to_delete", newTemplate)

        val success = manager.deleteTemplate("to_delete")

        assertTrue(success)
        assertNull(manager.getTemplate("to_delete"))
    }

    @Test
    fun `renderTemplate should replace variables`() {
        val variables = mapOf(
            "timestamp" to "2024-01-01 12:00:00",
            "service" to "api-server",
            "severity" to "ERROR",
            "error_message" to "Connection refused"
        )

        val rendered = manager.renderTemplate("error_autopilot", variables)

        assertTrue(rendered.body.contains("2024-01-01 12:00:00"))
        assertTrue(rendered.body.contains("api-server"))
        assertTrue(rendered.body.contains("ERROR"))
        assertTrue(rendered.body.contains("Connection refused"))
    }

    @Test
    fun `renderTemplate should use default template when id is null`() {
        val rendered = manager.renderTemplate(null, emptyMap())

        // Default template is error_autopilot
        assertTrue(rendered.title.startsWith("[ERROR]"))
    }

    @Test
    fun `renderTemplate should include service in title`() {
        val variables = mapOf(
            "service" to "api-server",
            "error_title" to "Connection Error"
        )

        val rendered = manager.renderTemplate("error_autopilot", variables)

        assertEquals("[ERROR] api-server: Connection Error", rendered.title)
    }

    @Test
    fun `renderTemplate should include labels from template`() {
        val rendered = manager.renderTemplate("error_autopilot", emptyMap())

        assertTrue(rendered.labels.contains("bug"))
        assertTrue(rendered.labels.contains("auto-generated"))
    }

    @Test
    fun `renderTemplate should handle list variables`() {
        val variables = mapOf(
            "affected_files" to listOf("file1.kt", "file2.kt")
        )

        val rendered = manager.renderTemplate("error_autopilot", variables)

        assertTrue(rendered.body.contains("file1.kt"))
        assertTrue(rendered.body.contains("file2.kt"))
    }

    @Test
    fun `renderTemplate should replace missing variables with NA`() {
        val rendered = manager.renderTemplate("error_autopilot", emptyMap())

        // Variables that weren't provided should be replaced with N/A
        assertTrue(rendered.body.contains("N/A"))
    }

    @Test
    fun `importFromGitHub should parse YAML front matter`() {
        val templateContent = """
            ---
            name: Bug Report
            title: "[BUG]"
            labels: bug, critical
            ---

            ## Description
            [Describe the bug]
        """.trimIndent()

        val template = manager.importFromGitHub(templateContent, "imported")

        assertNotNull(template)
        assertEquals("Bug Report", template.name)
        assertEquals("[BUG]", template.titlePrefix)
        assertTrue(template.labels.contains("bug"))
        assertTrue(template.labels.contains("critical"))
    }

    @Test
    fun `importFromGitHub should return null for invalid content`() {
        val invalidContent = "This is not a valid template"

        val template = manager.importFromGitHub(invalidContent, "invalid")

        assertNull(template)
    }
}
