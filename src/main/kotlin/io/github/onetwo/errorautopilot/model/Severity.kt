package io.github.onetwo.errorautopilot.model

enum class Severity {
    CRITICAL, ERROR, WARNING, INFO;

    companion object {
        fun fromString(value: String): Severity? = try {
            valueOf(value.uppercase())
        } catch (e: Exception) {
            null
        }
    }
}
