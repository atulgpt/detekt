package io.gitlab.arturbosch.detekt.rules.performance

import io.gitlab.arturbosch.detekt.api.Finding
import io.gitlab.arturbosch.detekt.rules.KotlinCoreEnvironmentTest
import io.gitlab.arturbosch.detekt.test.assertThat
import io.gitlab.arturbosch.detekt.test.compileAndLintWithContext
import io.gitlab.arturbosch.detekt.test.lintWithContext
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

// Testcases inspired from https://github.com/JetBrains/intellij-community/tree/master/plugins/kotlin/idea/tests/testData/intentions/convertLambdaToReference
@KotlinCoreEnvironmentTest
class DoubleLambdaSpec(private val env: KotlinCoreEnvironment) {
    private val subject = DoubleLambda()

    @Test
    fun `does not report when new lambda performs additional operation before calling passed lambda`() {
        val code = """
            import kotlin.concurrent.thread

            inline fun customInline(lambda: () -> Unit) {
                // no-op
            }

            fun test(lambda: () -> Unit) {
                thread {
                    print("Before lambda invoked")
                    lambda()
                }.start()

                customInline { 
                    print("Before lambda invoked")
                    lambda()
                }
            }
        """.trimIndent()

        val findings = subject.compileAndLintWithContext(env, code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report when new lambda performs additional operation after calling passed lambda`() {
        val code = """
            import kotlin.concurrent.thread

            inline fun customInline(lambda: () -> Unit) {
                // no-op
            }

            fun test(lambda: () -> Unit) {
                thread {
                    lambda()
                    print("after lambda invoked")
                }.start()

                customInline {
                    lambda()
                    print("after lambda invoked")
                }
            }
        """.trimIndent()

        val findings = subject.compileAndLintWithContext(env, code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report when new lambda is empty`() {
        val code = """
            import kotlin.concurrent.thread

            inline fun customInline(lambda: () -> Unit) {
                // no-op
            }

            fun test() {
                thread {
                    // no-op
                }.start()

                customInline { 
                    // no-op
                }
            }
        """.trimIndent()

        val findings = subject.compileAndLintWithContext(env, code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report when lambda is passed`() {
        val code = """
            import kotlin.concurrent.thread

            inline fun customInline(lambda: () -> Unit) {
                // no-op
            }

            fun test(lambda: () -> Unit) {
                thread(block = lambda).start()
                customInline(lambda = lambda)
            }
        """.trimIndent()

        val findings = subject.compileAndLintWithContext(env, code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report when passed lambda is not used directly`() {
        val code = """
            fun foo(f: (String) -> Unit) {}
            inline fun fooInline(f: (String) -> Unit) {}

            fun test(lambda: (String) -> (String) -> Int) {
                foo { s -> s.let(lambda).invoke(s) }
                fooInline { s -> s.let(lambda).invoke(s) }
            }
        """.trimIndent()

        val findings = subject.compileAndLintWithContext(env, code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report when passed constructor ref is not used directly`() {
        val code = """
            class A(s: String) {
                fun bar(s: String) {}
            }

            fun foo(f: (String) -> Unit) {}
            inline fun fooInline(f: (String) -> Unit) {}

            fun test() {
                foo { s -> s.let(::A).bar(s) }
                fooInline { s -> s.let(::A).bar(s) }
            }
        """.trimIndent()

        val findings = subject.compileAndLintWithContext(env, code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report when super method is called`() {
        val code = """
            open class A {
                open fun method() {}
            }

            class B : A() {
                override fun method() {
                    // reference to super call not supported
                    // https://youtrack.jetbrains.com/issue/KT-11520/Allow-callable-references-to-super-members
                    call { super.method() }
                }
            }

            fun call(f: () -> Unit) {
                f()
            }
        """.trimIndent()

        val findings = subject.compileAndLintWithContext(env, code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report when lambda is passed with additional parameter as well`() {
        val code = """
            inline fun test(lambda: (Int) -> Unit) {
                repeat(3, lambda)
            }
        """.trimIndent()

        val findings = subject.compileAndLintWithContext(env, code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report when available lambda type differs`() {
        val code = """
            import kotlin.concurrent.thread

            fun test(lambda: (Int) -> Unit) {
                thread {
                    lambda(1)
                }.start()
            }
        """.trimIndent()

        val findings = subject.compileAndLintWithContext(env, code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does report when new lambda just calls passed lambda in non inline fun`() {
        val code = """
            import kotlin.concurrent.thread

            fun custom(lambda: () -> Unit) {
                // no-op
            }

            fun test(lambda: () -> Unit) {
                custom {
                    lambda()
                }

                custom({
                    lambda()
                })

                custom(lambda = {
                    lambda()
                })

                custom {
                    lambda.invoke()
                }
            }
        """.trimIndent()

        val findings = subject.compileAndLintWithContext(env, code)
        assertThat(findings).hasSize(4)
        assertMessage(findings[0], "lambda()")
        assertMessage(findings[1], "lambda()")
        assertMessage(findings[2], "lambda()")
        assertMessage(findings[3], "lambda.invoke()")
    }

    @Test
    fun `does report when new lambda just calls passed lambda in parenthesized manner in non inline fun`() {
        val code = """
            import kotlin.concurrent.thread

            fun custom(lambda: () -> Unit) {
                // no-op
            }

            fun test(lambda: () -> Unit) {
                custom {
                    (lambda())
                }

                custom {
                    (lambda).invoke()
                }

                custom {
                    ((lambda)).invoke()
                }

                custom {
                    (lambda.invoke())
                }

                custom {
                    ((lambda.invoke()))
                }
            }
        """.trimIndent()

        val findings = subject.compileAndLintWithContext(env, code)
        assertThat(findings).hasSize(5)
        assertMessage(findings[0], "lambda()")
        assertMessage(findings[1], "(lambda).invoke()")
        assertMessage(findings[2], "((lambda)).invoke()")
        assertMessage(findings[3], "lambda.invoke()")
        assertMessage(findings[4], "lambda.invoke()")
    }

    @Test
    fun `does report when new parenthesized lambda just calls passed lambda in non inline fun`() {
        val code = """
            import kotlin.concurrent.thread

            fun custom(lambda: () -> Unit) {
                // no-op
            }

            fun test(lambda: () -> Unit) {
                custom(({
                    lambda()
                }))

                custom(lambda = ({
                    lambda()
                }))
            }
        """.trimIndent()

        val findings = subject.compileAndLintWithContext(env, code)
        assertThat(findings).hasSize(2)
        assertMessage(findings[0], "lambda()")
        assertMessage(findings[1], "lambda()")
    }

    @Test
    fun `does report when new lambda just calls passed custom lambda in non inline fun`() {
        val code = """
            import kotlin.concurrent.thread

            interface Block : () -> Unit {
                override fun invoke() {
                    /* no-op */
                }
            }

            fun test(lambda: Block) {
                thread {
                    lambda.invoke()
                }
            }
        """.trimIndent()

        val findings = subject.compileAndLintWithContext(env, code)
        assertThat(findings).hasSize(1)
        assertMessage(findings[0], "lambda.invoke()")
    }

    @Test
    fun `does report when new lambda just calls passed lambda in non inline fun which takes runnable`() {
        val code = """
            import java.lang.Runnable
            import kotlin.concurrent.thread

            fun action(lambda: Runnable) {
                println("")
            }

            fun test(lambda: () -> Unit) {
                action {
                    lambda()
                }
            }
        """.trimIndent()

        val findings = subject.compileAndLintWithContext(env, code)
        assertThat(findings).hasSize(1)
        assertMessage(findings[0], "lambda()")
    }

    @Test
    fun `does not report when passed custom lambda reference directly used`() {
        val code = """
            import kotlin.concurrent.thread

            interface Block : () -> Unit {
                override fun invoke() {
                    /* no-op */
                }
            }

            fun test(lambda: Block) {
                thread(block = lambda)
            }
        """.trimIndent()

        val findings = subject.compileAndLintWithContext(env, code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report when new non inline lambda just calls passed lambda in inline fun`() {
        val code = """
            fun test(lambda: () -> Unit) {
                repeat(1) {
                    lambda()
                }

                repeat(1, {
                    lambda()
                })

                repeat(1) {
                    (lambda())
                }

                repeat(1) {
                    lambda.invoke()
                }

                repeat(1) {
                    (lambda).invoke()
                }

                repeat(1) {
                    (lambda.invoke())
                }
            }
        """.trimIndent()

        val findings = subject.compileAndLintWithContext(env, code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report when new non inline lambda just calls passed lambda in inline fun with cross-inline modifier`() {
        val code = """
            inline fun customInline(crossinline lambda: () -> Unit) {
                // no-op
            }

            fun test(lambda: () -> Unit) {
                customInline {
                    lambda()
                }
            }
        """.trimIndent()

        val findings = subject.compileAndLintWithContext(env, code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does report when new non inline lambda just calls passed lambda in inline fun with no-inline modifier`() {
        val code = """
            inline fun customInline(noinline lambda: () -> Unit) {
                // no-op
            }

            fun test(lambda: () -> Unit) {
                customInline {
                    lambda()
                }
            }
        """.trimIndent()

        val findings = subject.compileAndLintWithContext(env, code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `does not report when new lambda calls passed lambda class method inside parenthesis instead of method ref in non inline fun`() {
        val code = """
            import kotlin.concurrent.thread

            fun test(lambda: () -> Unit) {
                thread {
                    lambda().toString()
                }.start()

                thread {
                    (lambda().toString())
                }.start()

                repeat(1) {
                    lambda().toString()
                }

                repeat(1) {
                    (lambda().toString())
                }
            }
        """.trimIndent()
        val findings = subject.compileAndLintWithContext(env, code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does report when new lambda call the passed lambda when function has generic type`() {
        val code = """
            fun <M>test1(value: M, lambda: (M) -> String) {
                sequenceOf(value).map { 
                    lambda(it)
                }
            }

            fun <M>test2(value: M, lambda: M.() -> String) {
                sequenceOf(value).map { 
                    lambda(it)
                }
            }

            fun <M>test3(value: M, lambda: M.() -> String) {
                sequenceOf(value).map { 
                    it.lambda()
                }
            }
        """.trimIndent()
        val findings = subject.compileAndLintWithContext(env, code)
        assertThat(findings).hasSize(3)
    }

    @Test
    fun `does not report when new lambda call the passed lambda when class has generic type`() {
        val code = """
            fun <M>test1(value: M, lambda: (M) -> String) {
                sequenceOf(value).map { 
                    lambda(it)
                }
            }

            fun <M>test2(value: M, lambda: M.() -> String) {
                sequenceOf(value).map { 
                    lambda(it)
                }
            }

//            fun <M>test3(value: M, lambda: M.() -> String) {
//                sequenceOf(value).map { 
//                    it.lambda()
//                }
//            }
        """.trimIndent()
        val findings = subject.compileAndLintWithContext(env, code)
        assertThat(findings).hasSize(2)
    }

    @Nested
    inner class WithExtension {
        @Test
        fun `does report when new lambda calls passed lambda in non inline fun`() {
            val code = """
                fun test(lambda: Int.() -> String) {
                    sequenceOf(1).map { it.lambda() }
                }
            """.trimIndent()

            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).hasSize(1)
            assertMessage(findings[0], "it.lambda()")
        }

        @Test
        fun `does not report when new lambda calls passed lambda`() {
            val code = """
                fun test(lambda: Int.() -> String) {
                    listOf(1).map(lambda)
                    sequenceOf(1).map(lambda)
                }
            """.trimIndent()

            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).isEmpty()
        }

        @Test
        fun `does not report when method ref is passed directly`() {
            val code = """
                fun Int.toDomain() = this.toString()

                fun test() {
                    listOf(1).map(Int::toDomain)
                    sequenceOf(1).map(Int::toDomain)
                }
            """.trimIndent()

            val findings = subject.lintWithContext(env, code)
            assertThat(findings).isEmpty()
        }

        @Test
        fun `does not report when new lambda just calls method instead of using method ref`() {
            val code = """
                fun Int.toDomain() = this.toString()

                fun test() {
                    listOf(1).map { it.toDomain() }
                    sequenceOf(1).map { it.toDomain() }
                }
            """.trimIndent()

            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).isEmpty()
        }

        @Test
        fun `does not report when new lambda just calls method with a constant`() {
            val code = """
                fun Int.toDomain() = this.toString()

                fun test(lambda: Int.() -> String) {
                    listOf(1).map { 5.toDomain() }
                    sequenceOf(1).map { 5.toDomain() }
                    listOf(1).map { 5.lambda() }
                    sequenceOf(1).map { 5.lambda() }
                }
            """.trimIndent()

            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).isEmpty()
        }

        @Test
        fun `does not report when new lambda parameter is satisfied with a variable`() {
            val code = """
                fun Int.toDomain() = this.toString()

                fun test(lambda: Int.() -> String) {
                    val a = 9
                    listOf(1).map { a.toDomain() }
                    sequenceOf(1).map { a.toDomain() }
                    listOf(1).map { a.lambda() }
                    sequenceOf(1).map { a.lambda() }
                }
            """.trimIndent()

            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).isEmpty()
        }
    }

    @Nested
    inner class WithParameter {
        @Test
        fun `does report when new lambda called inside a lambda in non inline fun`() {
            val code = """
                fun test(lambda: (Int) -> String) {
                    sequenceOf(1).map { lambda(it) }
                }
            """.trimIndent()

            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).hasSize(1)
            assertMessage(findings[0], "lambda(it)")
        }

        @Test
        fun `does not report when new lambda just calls passed lambda inside inline fun`() {
            val code = """
                fun test(lambda: (Int) -> String) {
                    listOf(1).map { lambda(it) }
                }
            """.trimIndent()

            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).isEmpty()
        }

        @Test
        fun `does not report when new lambda is passed`() {
            val code = """
                fun test(lambda: (Int) -> String) {
                    listOf(1).map(lambda)
                    sequenceOf(1).map(lambda)
                }
            """.trimIndent()

            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).isEmpty()
        }

        @Test
        fun `does not report when new lambda just calls method instead of method ref`() {
            val code = """
                fun toDomain(i: Int) = i.toString()

                fun test() {
                    listOf(1).map { toDomain(it) }
                    sequenceOf(1).map { toDomain(it) }
                }
            """.trimIndent()

            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).isEmpty()
        }

        @Test
        fun `does not report when method ref is passed directly`() {
            val code = """
                fun toDomain(i: Int) = i.toString()

                fun test() {
                    listOf(1).map(::toDomain)
                    sequenceOf(1).map(::toDomain)
                }
            """.trimIndent()

            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).isEmpty()
        }

        @Test
        fun `does not report when using callable reference with two parameter`() {
            val code = """
                fun main(lambda: (Int, Int) -> Int) {
                   val f: (Int, Int) -> Int = lambda
                   println(f(10, 20))
                }
            """.trimIndent()

            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).isEmpty()
        }

        @Test
        fun `does report when using add is called inside a lambda with two parameter`() {
            val code = """
                fun main(lambda: (Int, Int) -> Int) {
                   val f: (Int, Int) -> Int = { a, b ->
                        lambda(a, b)
                    }
                   println(f(10, 20))
                }
            """.trimIndent()

            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).hasSize(1)
            assertMessage(findings[0], "lambda(a, b)")
        }

        @Test
        fun `does not report when using add is called inside a lambda with two parameter which can be replaced with method ref`() {
            val code = """
                fun add(a: Int, b: Int) = a + b

                fun main() {
                   val f: (Int, Int) -> Int = { a, b ->
                        add(a, b)
                    }
                   println(f(10, 20))
                }
            """.trimIndent()

            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).isEmpty()
        }

        @Test
        fun `does report with lambda is called with type parameters in non inline fun`() {
            val code = """
                fun <M> test(block: (M) -> Unit) {
                    println("invoked")
                }

                fun <M>test2(block: (M) -> Unit) {
                    test<M> { 
                        block(it)
                    }
                }
            """.trimIndent()
            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).hasSize(1)
        }

        @Test
        fun `does not report with lambda is called with type parameters in inline fun`() {
            val code = """
                inline fun <M> test(block: (M) -> Unit) {
                    println("invoked")
                }

                fun <M>test2(block: (M) -> Unit) {
                    test<M> { 
                        block(it)
                    }
                }
            """.trimIndent()
            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).isEmpty()
        }

        @Test
        fun `does not report when parameter lambda has parameter and extension and called function signature doesn't have it`() {
            val code = """
                fun body(receiver: String.(String) -> Unit) {
                    // no-op
                }

                fun usage(lambda: (String) -> Unit) {
                    body { lambda(it) }
                }
            """.trimIndent()
            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).isEmpty()
        }

        @Test
        fun `does report when parameter lambda has parameter and extension and called function two parameter in same order`() {
            val code = """
                fun body(receiver: String.(String) -> Unit) {
                    // no-op
                }

                fun usage(lambda: (String, String) -> Unit) {
                    body { lambda(this, it) }
                }
            """.trimIndent()
            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).hasSize(1)
            assertMessage(findings[0], "lambda(this, it)")
        }

        @Test
        fun `does not report when parameter lambda has parameter and extension and called function two parameter with same this`() {
            val code = """
                fun body(receiver: String.(String) -> Unit) {
                    // no-op
                }

                fun usage(lambda: (String, String) -> Unit) {
                    body { lambda(this, this) }
                }
            """.trimIndent()
            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).isEmpty()
        }

        @Test
        fun `does not report when parameter lambda has parameter and extension and called function two parameter but order is reversed`() {
            val code = """
                fun body(receiver: String.(String) -> Unit) {
                    // no-op
                }

                fun usage(lambda: (String, String) -> Unit) {
                    body { lambda(it, this) }
                }
            """.trimIndent()
            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).isEmpty()
        }

        @Test
        fun `does report when parameter lambda has parameter and extension with different types and called function two parameter in same order`() {
            val code = """
                fun body(receiver: Int.(String) -> Unit) {
                    // no-op
                }

                fun usage(lambda: (Int, String) -> Unit) {
                    body { lambda(this, it) }
                }
            """.trimIndent()
            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).hasSize(1)
            assertMessage(findings[0], "lambda(this, it)")
        }

        @Test
        fun `does not report when parameter lambda has parameter and extension with different types and called function two parameter in different order`() {
            val code = """
                fun body(receiver: Int.(String) -> Unit) {
                    // no-op
                }

                fun usage(lambda: (String, Int) -> Unit) {
                    body { lambda(it, this) }
                }
            """.trimIndent()
            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).isEmpty()
        }

        @Test
        fun `does not report when parameter lambda has parameters but lambda called with wrong order`() {
            val code = """
                fun body(receiver: (String, String, String) -> Unit) {
                    // no-op
                }

                fun usage(lambda: (String, String, String) -> Unit) {
                    body { it1, it2, it3 -> lambda(it1, it3, it2) }
                    body { it1, it2, _ -> lambda(it1, it2, it2) }
                }
            """.trimIndent()
            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).isEmpty()
        }

        @Test
        fun `does report when new lambda calls passed lambda with invoke and extension variable in non inline fun`() {
            val code = """
                fun customFun(lambda: Int.(String) -> String) { /* no-op */ }
                fun test(lambda: Int.(String) -> String) {
                    customFun { 
                        lambda.invoke(this, it)
                    }
                }
            """.trimIndent()

            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).hasSize(1)
            assertMessage(findings[0], "lambda.invoke(this, it)")
        }

        @Test
        fun `does report when new lambda calls passed lambda with invoke and extension variable in non inline fun with reversed order`() {
            val code = """
                fun customFun(lambda: String.(String) -> String) { /* no-op */ }
                fun test(lambda: String.(String) -> String) {
                    customFun { 
                        lambda.invoke(it, this)
                    }
                }
            """.trimIndent()

            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).isEmpty()
        }

        @Test
        fun `does report when parameter lambda has parameter and function lambda has extension`() {
            val code = """
                fun body(receiver: String.() -> Unit) {
                    // no-op
                }

                fun usage(lambda: (String) -> Unit) {
                    body {
                        lambda(this)
                    }
                }
            """.trimIndent()
            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).hasSize(1)
        }

        @Test
        fun `does report when parameter lambda has extension and function lambda has parameter`() {
            val code = """
                fun body(receiver: (String) -> Unit) {
                    // no-op
                }

                fun usage(lambda: String.() -> Unit) {
                    body {
                        lambda(it)
                    }
                }
            """.trimIndent()
            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).hasSize(1)
        }

        @Test
        fun `does report when lambda name is super and called instead of passed in non inline fun`() {
            val code = """
                fun test(`super`: (Int) -> Int) {
                    sequenceOf(1).map { `super`(it) }
                }
            """.trimIndent()
            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).hasSize(1)
            assertMessage(findings[0], "`super`(it)")
        }

        @Test
        fun `does non report when lambda name is super and called instead of passed in inline fun`() {
            val code = """
                fun test(`super`: (Int) -> Int) {
                    listOf(1).map { `super`(it) }
                }
            """.trimIndent()
            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).isEmpty()
        }

        @Test
        fun `does not report when function name is super and can be passed as method ref`() {
            val code = """
                fun `super`(x: Int): Int = TODO()

                fun test() {
                    sequenceOf(1).map { `super`(it) }
                    listOf(1).map { `super`(it) }
                }
            """.trimIndent()
            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).isEmpty()
        }

        @Test
        fun `does report when parameter lambda has extension and called non inline function signature also have it`() {
            val code = """
                fun body(receiver: String.(String) -> Unit) {
                    // no-op
                }

                fun usage(lambda: String.(String) -> Unit) {
                    body {
                        lambda(it)
                    }
                }
            """.trimIndent()
            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).hasSize(1)
            assertMessage(findings[0], "lambda(it)")
        }

        @Test
        fun `does not report when parameter lambda has extension and called inline function signature also have it`() {
            val code = """
                inline fun body(receiver: String.(String) -> Unit) {
                    // no-op
                }

                fun usage(lambda: String.(String) -> Unit) {
                    body {
                        lambda(it)
                    }
                }
            """.trimIndent()
            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).isEmpty()
        }

        @Test
        fun `does not report when parameter lambda has extension and called function signature also have it which can be passed as method ref`() {
            val code = """
                fun String.callMe(s: String) {
                    println(this)
                }

                fun body(receiver: String.(String) -> Unit) {
                    // no-op
                }

                fun bodyInline(receiver: String.(String) -> Unit) {
                    // no-op
                }

                fun usage() {
                    body {
                        callMe(it)
                    }

                    bodyInline {
                        callMe(it)
                    }
                }
            """.trimIndent()
            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).isEmpty()
        }

        @Test
        fun `does not report when parameter lambda has extension and is not called with _it_`() {
            val code = """
                fun body(receiver: String.(String) -> Unit) {
                    // no-op
                }

                inline fun bodyInline(receiver: String.(String) -> Unit) {
                    // no-op
                }

                fun usage(lambda: String.(String) -> Unit) {
                    body {
                        lambda("passed string values")
                    }

                    bodyInline {
                        lambda("passed string values")
                    }
                }
            """.trimIndent()
            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).isEmpty()
        }


        @Test
        fun `does report when lambda is called with one extension and one input param with non inline fun having lambda with two parameters`() {
            val code = """
                data class Seed(val real: String)

                fun test(lambda: Seed.(Seed) -> Seed) {
                    sequenceOf(Seed("seed1"), Seed("seed2")).zipWithNext { a, b ->
                        a.lambda(b)
                    }
                }
            """.trimIndent()

            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).hasSize(1)
            assertMessage(findings[0], "a.lambda(b)")
        }

        @Test
        fun `does not report when method reference with one extension and one input param is used outside the class`() {
            val code = """
                data class Seed(val real: String)

                fun test(lambda: Seed.(Seed) -> Seed) {
                    listOf(Seed("seed1"), Seed("seed2")).zipWithNext(lambda)
                    sequenceOf(Seed("seed1"), Seed("seed2")).zipWithNext(lambda)
                }
            """.trimIndent()

            val findings = subject.lintWithContext(env, code)
            assertThat(findings).isEmpty()
        }

        @Test
        fun `does report when lambda calls overloaded non inline fun where type can be fixed in vararg parent fun`() {
            val code = """
                fun <T> ambiguityFun(vararg fn: (T) -> Unit) {}

                fun overloadContext(lambda: (String) -> Unit) {
                    ambiguityFun({ x: String -> lambda(x) }, lambda)
                }
            """.trimIndent()

            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).hasSize(1)
        }

        @Test
        fun `does not report when lambda is passed in overloaded non inline fun where type can be fixed in vararg parent fun`() {
            val code = """
                fun <T> ambiguityFun(vararg fn: (T) -> Unit) {}

                fun overloadContext(lambda: (String) -> Unit) {
                    ambiguityFun(lambda, lambda)
                }
            """.trimIndent()

            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).isEmpty()
        }

        @Test
        fun `does not report when lambda is called in overloaded non inline fun where type is different`() {
            val code = """
                fun <T> ambiguityFun(vararg fn: (T) -> Unit) {}

                fun overloadContext(lambda1: (String) -> Unit, lambda2: (Int) -> Unit) {
                    ambiguityFun(lambda1, { x: String -> lambda2(x.toInt()) })
                }
            """.trimIndent()

            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).isEmpty()
        }

        @Test
        fun `does not report when using lambda ref changes the overload resolution`() {
            val code = """
                fun foo(lambda: (Int) -> Unit) {}
                fun foo(lambda: (Int, Int) -> Unit) {}

                fun test(lambda: (Int) -> Unit) {
                    foo { a, _ ->
                        lambda(a)
                    }
                }
            """.trimIndent()

            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).isEmpty()
        }

        @Test
        fun `does not report when using lambda ref changes the overload resolution with default parameter 1`() {
            val code = """
                fun foo(lambda1: (Int) -> Unit = { }, lambda2: (Int, Int) -> Unit = { _, _ -> }) {}
                fun test(lambda: (Int) -> Unit) {
                    foo { a, _ ->
                        lambda(a)
                    }
                }
            """.trimIndent()

            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).isEmpty()
        }

        @Test
        fun `does not report when using lambda ref changes the overload resolution with default parameter 2`() {
            val code = """
                fun foo(lambda1: (Int) -> Unit = { }, lambda2: (Int, Int) -> Unit = { _, _ -> }) {}
                fun test(lambda: (Int, Int) -> Unit) {
                    foo ({ a ->
                        lambda(a, 0)
                    })
                }
            """.trimIndent()

            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).isEmpty()
        }
    }

    @Nested
    inner class WithClassMethod {
        @Test
        fun `does not report when new lambda just calls instance method with a constant which can be passed as method ref`() {
            val code = """
                fun test() {
                    listOf(1).map { 5.times(it) }
                    sequenceOf(1).map { 5.times(it) }
                }
            """.trimIndent()

            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).isEmpty()
        }

        @Test
        fun `does not report when new lambda just calls instance method with a variable which can be passed as method ref`() {
            val code = """
                fun test() {
                    val a = 9
                    listOf(1).map { a.times(it) }
                    sequenceOf(1).map { a.times(it) }
                }
            """.trimIndent()

            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).isEmpty()
        }

        @Test
        fun `does not report when companion member method is called inside the class which can passed as method ref`() {
            val code = """
                class Foo {
                    companion object {
                        fun create(x: String): Foo = Foo()
                    }
                }

                fun main(args: Array<String>) {
                    listOf("a").map { Foo.create(it) }
                }
            """.trimIndent()

            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).isEmpty()
        }

        @Test
        fun `does not report when class member method is called outside the class which can be passed as method ref`() {
            val code = """
                class C {
                    fun toDomain() = System.currentTimeMillis().toString()
                }

                fun test() {
                    listOf(C()).map {
                        it.toDomain()
                    }
                }
            """.trimIndent()

            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).isEmpty()
        }

        @Test
        fun `does not report when class member method reference is used outside the class`() {
            val code = """
                class C {
                    fun toDomain() = System.currentTimeMillis().toString()
                }

                fun test() {
                    listOf(C()).map(C::toDomain)
                }
            """.trimIndent()

            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).isEmpty()
        }

        @Test
        fun `does not report when class member method is called outside the class with new class creation`() {
            val code = """
                class C {
                    fun toDomain(i: Int) = System.currentTimeMillis().toString()
                }

                fun test() {
                    listOf(1).map {
                        C().toDomain(it)
                    }
                }
            """.trimIndent()

            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).isEmpty()
        }

        @Test
        fun `does not report when class member extension method is called`() {
            val code = """
                class C {
                    private fun Int.toDomain() = this.toString()

                    private fun test() {
                        listOf(1).map {
                            it.toDomain()
                        }
                    }
                }
            """.trimIndent()

            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).isEmpty()
        }

        @Test
        fun `does not report when lambda calls reified method in a class`() {
            val code = """
                class Foo {
                    inline fun <reified T: Any> bar(): String? = T::class.simpleName
                }

                fun test(list: List<Foo>) {
                    list.forEach { it.bar<Int>() }
                }
            """.trimIndent()
            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).isEmpty()
        }

        @Test
        fun `does not report when lambda calls reified method in a class with non reified method is present`() {
            val code = """
                class Foo {
                    inline fun <reified T: Any> bar(): String? = T::class.simpleName
                    fun bar() {}
                }

                fun test(list: List<Foo>) {
                    list.forEach { it.bar<Int>() } // list.forEach(::bar) calls second method
                }
            """.trimIndent()
            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).isEmpty()
        }

        @Test
        fun `does not report when invoke operator reference is used`() {
            val code = """
                fun myInvoke(f: () -> Unit) = f()

                class InvokeContainer {
                    operator fun invoke() {}
                }

                fun test(k: InvokeContainer) {
                    myInvoke(k::invoke)
                }
            """.trimIndent()
            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).isEmpty()
        }
    }

    @Nested
    inner class WithConstructor {
        @Test
        fun `does not report when class class constructor with out parameter is called`() {
            val code = """
                class C

                fun create(factory: () -> C) {
                    val x: C = factory()
                }

                fun test() {
                    create { C() }
                }
            """.trimIndent()

            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).isEmpty()
        }

        @Test
        fun `does not report when class constructor is called reference via package`() {
            val code = """
                package com.example

                class MyClass(val value: Int)

                fun f(body: (Int) -> com.example.MyClass) {}

                fun test() {
                    f { i -> com.example.MyClass(i) }
                }
            """.trimIndent()

            val findings = subject.lintWithContext(env, code)
            assertThat(findings).isEmpty()
        }
    }

    @Nested
    inner class WithVariable {
        @Test
        fun `does not report when new variable assignment creates method ref or lambda with method can be replaced with method`() {
            val code = """
                val a: (Int) -> String = { it.toString() }
                val b: (Any) -> Boolean = { a -> Int::class.isInstance(a) }
                val c: (Int) -> String = Int::toString
            """.trimIndent()

            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).isEmpty()
        }

        @Test
        fun `does report when new variable assignment creates parameter lambda which call another parameter lambda`() {
            val code = """
                fun test(lambda: (Int) -> String) {
                    val a: (Int) -> String = { lambda(it) }
                    val b: (Int) -> String = { lambda.invoke(it) }
                }
            """.trimIndent()

            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).hasSize(2)
        }

        @Test
        fun `does report when new variable assignment creates parameter lambda which call another extension lambda`() {
            val code = """
                fun test(lambda: Int.() -> String) {
                    val a: (Int) -> String = { lambda(it) }
                }
            """.trimIndent()

            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).hasSize(1)
        }

        @Test
        fun `does report when new variable assignment creates extension lambda which call another parameter lambda`() {
            val code = """
                fun test(lambda: (Int) -> String) {
                    val a: Int.() -> String = { lambda(this) }
                }
            """.trimIndent()

            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).hasSize(1)
        }

        @Test
        fun `does report when new variable assignment creates extension and parameter lambda which call another parameter lambda in correct order`() {
            val code = """
                fun test(lambda: Int.(Int) -> String) {
                    val a: Int.(Int) -> String = { this.lambda(it) }
                }
            """.trimIndent()

            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).hasSize(1)
        }

        @Test
        fun `does not report when new variable assignment creates extension and parameter lambda which call another parameter lambda in reverse order`() {
            val code = """
                fun test(lambda: Int.(Int) -> String) {
                    val a: Int.(Int) -> String = { it.lambda(this) }
                }
            """.trimIndent()

            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).isEmpty()
        }

        @Test
        fun `does report when new variable assignment creates lambda which call another parameter and extension lambda`() {
            val code = """
                fun test(lambda: String.(Int) -> String) {
                    val a: (String, Int) -> String = { a, b -> a.lambda(b) }
                }
            """.trimIndent()

            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).hasSize(1)
        }

        @Test
        fun `does not report when using variable reference changes the lambda signature`() {
            val code = """
                fun test(lambda: (Int) -> String) {
                    val a = { a: Int, _: Int -> lambda(a) }
                }
            """.trimIndent()

            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).isEmpty()
        }

        @Test
        fun `does not report when lambda returns top level property`() {
            val code = """
                const val NAME = "Kotlin"
                val x = { obj: Any -> NAME }
            """.trimIndent()

            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).isEmpty()
        }

        @Test
        fun `does report when property lambda is called in new lambda with Class ref`() {
            val code = """
                object LambdaHolder {
                    val lambda: (Int) -> String = { it.toString() }
                }

                fun test() {
                    sequenceOf(1).map {
                        LambdaHolder.lambda(it)
                    }
                    sequenceOf(1).map {
                        (LambdaHolder).lambda(it)
                    }
                    sequenceOf(1).map {
                        (LambdaHolder.lambda(it))
                    }
                }
            """.trimIndent()

            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).hasSize(3)
        }

        @Test
        fun `does report when property lambda is called in new lambda with Class and package ref`() {
            val code = """
                package com.example
                object LambdaHolder {
                    val lambda: (Int) -> String = { it.toString() }
                }

                fun test() {
                    sequenceOf(1).map {
                        com.example.LambdaHolder.lambda(it)
                    }
                    sequenceOf(1).map {
                        (com.example.LambdaHolder).lambda(it)
                    }
                    sequenceOf(1).map {
                        (com.example.LambdaHolder.lambda(it))
                    }
                }
            """.trimIndent()

            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).hasSize(3)
        }
    }

    @Nested
    inner class WithSuspension {
        @Test
        fun `does not report when suspend function is called in non-suspending inline method`() {
            val code = """
                suspend fun x(lambda: suspend String.() -> Unit) {
                    listOf("Jack", "Tom").forEach { lambda() }
                }
            """.trimIndent()

            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).isEmpty()
        }

        @Test
        fun `does report when non suspending lambda is called in suspending lambda`() {
            val code = """
                fun coroutine(block: suspend () -> Unit) {}

                fun test(action: () -> Unit) = coroutine {
                    action()
                }
            """.trimIndent()

            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).hasSize(1)
        }

        @Test
        fun `does not report when non suspending lambda is passed in suspending lambda`() {
            val code = """
                fun coroutine(block: suspend () -> Unit) {}

                fun test(action: () -> Unit) = coroutine(action)
            """.trimIndent()

            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).isEmpty()
        }

        @Test
        fun `does report when suspending lambda is called in suspend lambda`() {
            val code = """
                fun foo(a: suspend () -> Unit) {}

                fun usage(action: suspend () -> Unit) {
                    foo { action() }
                }
            """.trimIndent()

            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).hasSize(1)
            assertMessage(findings[0], "action()")
        }

        @Test
        fun `does not report when suspending lambda is passed in suspend lambda`() {
            val code = """
                fun foo(a: suspend () -> Unit) {}

                fun usage(action: suspend () -> Unit) {
                    foo(action)
                }
            """.trimIndent()

            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).isEmpty()
        }
    }

    private fun assertMessage(finding: Finding, expressionStr: String) {
        assertThat(finding).hasMessage(
            "Use passed lambda $expressionStr directly " +
                "instead of calling it within new lambda. This incurs a performance penalty."
        )
    }
}
