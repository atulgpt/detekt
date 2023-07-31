package io.gitlab.arturbosch.detekt.rules.bugs

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.rules.KotlinCoreEnvironmentTest
import io.gitlab.arturbosch.detekt.test.compileAndLintWithContext
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.junit.jupiter.api.Test

@KotlinCoreEnvironmentTest
class ImplicitDefaultLocaleSpec(private val env: KotlinCoreEnvironment) {
    private val subject = ImplicitDefaultLocale(Config.empty)

    @Test
    fun `reports String_format call with template but without explicit locale`() {
        val code = """
            fun x() {
                String.format("%d", 1)
            }
        """.trimIndent()
        assertThat(subject.compileAndLintWithContext(env, code)).hasSize(1)
    }

    @Test
    fun `does not report String_format call with explicit locale`() {
        val code = """
            import java.util.Locale
            fun x() {
                String.format(Locale.US, "%d", 1)
            }
        """.trimIndent()
        assertThat(subject.compileAndLintWithContext(env, code)).isEmpty()
    }

    @Test
    fun `reports String_uppercase() call without explicit locale`() {
        val code = """
            fun x() {
                val s = "deadbeef"
                s.uppercase()
            }
        """.trimIndent()
        assertThat(subject.compileAndLintWithContext(env, code)).hasSize(1)
    }

    @Test
    fun `does not report String_uppercase() call with explicit locale`() {
        val code = """
            import java.util.Locale
            fun x() {
                val s = "deadbeef"
                s.uppercase(Locale.US)
            }
        """.trimIndent()
        assertThat(subject.compileAndLintWithContext(env, code)).isEmpty()
    }

    @Test
    fun `reports String_lowercase() call without explicit locale`() {
        val code = """
            fun x() {
                val s = "deadbeef"
                s.lowercase()
            }
        """.trimIndent()
        assertThat(subject.compileAndLintWithContext(env, code)).hasSize(1)
    }

    @Test
    fun `does not report String_lowercase() call with explicit locale`() {
        val code = """
            import java.util.Locale
            fun x() {
                val s = "deadbeef"
                s.lowercase(Locale.US)
            }
        """.trimIndent()
        assertThat(subject.compileAndLintWithContext(env, code)).isEmpty()
    }

    @Test
    fun `reports nullable String_uppercase call without explicit locale`() {
        val code = """
            fun x() {
                val s: String? = "deadbeef"
                s?.uppercase()
            }
        """.trimIndent()
        assertThat(subject.compileAndLintWithContext(env, code)).hasSize(1)
    }

    @Test
    fun `reports nullable String_lowercase call without explicit locale`() {
        val code = """
            fun x() {
                val s: String? = "deadbeef"
                s?.lowercase()
            }
        """.trimIndent()
        assertThat(subject.compileAndLintWithContext(env, code)).hasSize(1)
    }
}
