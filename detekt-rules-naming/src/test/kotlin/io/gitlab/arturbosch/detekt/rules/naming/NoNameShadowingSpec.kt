package io.gitlab.arturbosch.detekt.rules.naming

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.rules.KotlinCoreEnvironmentTest
import io.gitlab.arturbosch.detekt.test.assertThat
import io.gitlab.arturbosch.detekt.test.compileAndLintWithContext
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.junit.jupiter.api.Test

@KotlinCoreEnvironmentTest
class NoNameShadowingSpec(val env: KotlinCoreEnvironment) {
    private val subject = NoNameShadowing(Config.empty)

    @Test
    fun `report shadowing variable`() {
        val code = """
            fun test(i: Int) {
                val i = 1
            }
        """.trimIndent()
        val findings = subject.compileAndLintWithContext(env, code)
        assertThat(findings).singleElement().hasMessage("Name shadowed: i")
        assertThat(findings).hasStartSourceLocation(2, 9)
    }

    @Test
    fun `report shadowing instance variable with local variable`() {
        val code = """
            val i = 1
            fun test() {
                val i = 1
            }
        """.trimIndent()
        val findings = subject.compileAndLintWithContext(env, code)
        assertThat(findings).singleElement().hasMessage("Name shadowed: i")
        assertThat(findings).hasStartSourceLocation(3, 9)
    }

    @Test
    fun `report shadowing instance variable with param variable`() {
        val code = """
            val i = 1
            fun test(i: Int) {
                println(i)
            }
        """.trimIndent()
        val findings = subject.compileAndLintWithContext(env, code)
        assertThat(findings).singleElement().hasMessage("Name shadowed: i")
        assertThat(findings).hasStartSourceLocation(2, 10)
    }

    @Test
    fun `does not report shadowing instance variable in class which can not be accessed`() {
        val code = """
            class A {
                val i = 1
            }
            fun test() {
                val i = 1
            }
        """.trimIndent()
        val findings = subject.compileAndLintWithContext(env, code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `report shadowing companion instance variable in a class`() {
        val code = """
            class A {
                fun foo() {
                    val i = 1
                }
                companion object {
                    const val i = 1
                }
            }
        """.trimIndent()
        val findings = subject.compileAndLintWithContext(env, code)
        assertThat(findings).singleElement().hasMessage("Name shadowed: i")
    }

    @Test
    fun `report shadowing destructuring declaration entry`() {
        val code = """
            fun test(j: Int) {
                val (j, _) = 1 to 2
            }
        """.trimIndent()
        val findings = subject.compileAndLintWithContext(env, code)
        assertThat(findings).singleElement().hasMessage("Name shadowed: j")
    }

    @Test
    fun `report shadowing lambda parameter`() {
        val code = """
            fun test(k: Int) {
                listOf(1).map { k ->
                }
            }
        """.trimIndent()
        val findings = subject.compileAndLintWithContext(env, code)
        assertThat(findings).singleElement().hasMessage("Name shadowed: k")
    }

    @Test
    fun `report shadowing nested lambda 'it' parameter`() {
        val code = """
            fun test() {
                listOf(1).forEach {
                    listOf(2).forEach { it ->
                    }
                }
            }
        """.trimIndent()
        val findings = subject.compileAndLintWithContext(env, code)
        assertThat(findings).singleElement().hasMessage("Name shadowed: it")
    }

    @Test
    fun `does not report when implicit 'it' parameter isn't used`() {
        val code = """
            fun test() {
                listOf(1).forEach {
                    listOf(2).forEach {
                    }
                }
            }
        """.trimIndent()
        val findings = subject.compileAndLintWithContext(env, code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report not shadowing param variable`() {
        val code = """
            fun test(i: Int) {
                val j = i
            }
        """.trimIndent()
        val findings = subject.compileAndLintWithContext(env, code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report not shadowing instance variable`() {
        val code = """
            val i = 1
            fun test() {
                val j = i
            }
        """.trimIndent()
        val findings = subject.compileAndLintWithContext(env, code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report not shadowing nested lambda implicit 'it' parameter`() {
        val code = """
            fun test() {
                listOf(1).forEach { i ->
                    listOf(2).forEach {
                        println(it)
                    }
                }
                "".run {
                    listOf(2).forEach {
                        println(it)
                    }
                }
                listOf("").let { list ->
                    list.map { it + "x" }
                }
            }
        """.trimIndent()
        val findings = subject.compileAndLintWithContext(env, code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `#7741 does report shadowing when inside a lambda`() {
        val code = """
            fun asdf(rotation: Float, onClick: (Float) -> Unit) {
                foo {
                    asdf(rotation) { rotation -> onClick(rotation) }
                }
            }

            fun foo(block: () -> Unit) {
            }
        """.trimIndent()
        val findings = subject.compileAndLintWithContext(env, code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `does not report shadowing when type and variable name match`() {
        val code = """
            fun foo(onClick: (Float) -> Unit) {
                listOf(1F).map { Float ->
                    onClick(Float)
                }
            }
        """.trimIndent()
        val findings = subject.compileAndLintWithContext(env, code)
        assertThat(findings).isEmpty()
    }
}
