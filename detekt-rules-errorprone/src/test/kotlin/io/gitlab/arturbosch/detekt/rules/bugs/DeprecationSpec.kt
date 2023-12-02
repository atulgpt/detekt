package io.gitlab.arturbosch.detekt.rules.bugs

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.rules.KotlinCoreEnvironmentTest
import io.gitlab.arturbosch.detekt.test.compileAndLintWithContext
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.cli.common.setupLanguageVersionSettings
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.junit.jupiter.api.Test

@KotlinCoreEnvironmentTest
class DeprecationSpec(private val env: KotlinCoreEnvironment) {
    private val subject = Deprecation(Config.empty)

    @Test
    fun `reports when supertype is deprecated`() {
        val code = """
            @Deprecated("deprecation message")
            abstract class Foo {
                abstract fun bar() : Int
            
                fun baz() {
                }
            }
            
            abstract class Oof : Foo() {
                fun spam() {
                }
            }
        """.trimIndent()
        val findings = subject.compileAndLintWithContext(env, code)
        assertThat(findings).hasSize(1)
        assertThat(findings.first().message).isEqualTo("""Foo is deprecated with message "deprecation message"""")
    }

    @Test
    fun `does not report when supertype is not deprecated`() {
        val code = """
            abstract class Oof : Foo() {
                fun spam() {
                }
            }
            abstract class Foo {
                abstract fun bar() : Int
            
                fun baz() {
                }
            }
        """.trimIndent()
        assertThat(subject.compileAndLintWithContext(env, code)).isEmpty()
    }

    @Test
    fun `does not report when language settings is below than DeprecatedSinceKotlin version`() {
        val code = """
            @Deprecated("deprecation message")
            @DeprecatedSinceKotlin(warningSince = "1.4")
            fun foo() {
                /* no-op */
            }
            
            fun test() {
                foo()
            }
        """.trimIndent()
        val v = object : CommonCompilerArguments() {}
        v.languageVersion = "1.3"
        env.configuration.setupLanguageVersionSettings(v)
        assertThat(subject.compileAndLintWithContext(env, code)).isEmpty()
    }

    @Test
    fun `does report when language settings is above than DeprecatedSinceKotlin version`() {
        val code = """
            @Deprecated("deprecation message")
            @DeprecatedSinceKotlin(warningSince = "1.4")
            fun foo() {
                /* no-op */
            }
            
            fun test() {
                foo()
            }
        """.trimIndent()
        val compilerArgs = object : CommonCompilerArguments() {}
        compilerArgs.languageVersion = "1.6"
        env.configuration.setupLanguageVersionSettings(compilerArgs)
        assertThat(subject.compileAndLintWithContext(env, code)).hasSize(1)
    }

    @Test
    fun `does report when language settings is equal to DeprecatedSinceKotlin version`() {
        val code = """
            @Deprecated("deprecation message")
            @DeprecatedSinceKotlin(warningSince = "1.4")
            fun foo() {
                /* no-op */
            }
            
            fun test() {
                foo()
            }
        """.trimIndent()
        val compilerArgs = object : CommonCompilerArguments() {}
        compilerArgs.languageVersion = "1.4"
        env.configuration.setupLanguageVersionSettings(compilerArgs)
        assertThat(subject.compileAndLintWithContext(env, code)).hasSize(1)
    }
}
