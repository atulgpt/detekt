package io.gitlab.arturbosch.detekt.rules.style

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.rules.KotlinCoreEnvironmentTest
import io.gitlab.arturbosch.detekt.test.compileAndLintWithContext
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@KotlinCoreEnvironmentTest
class UnnecessaryLetSpec(val env: KotlinCoreEnvironment) {
    val subject = UnnecessaryLet(Config.empty)

    @Test
    fun `reports unnecessary lets that can be changed to ordinary method call 1`() {
        val findings = subject.compileAndLintWithContext(
            env,
            """
                fun f() {
                    val a: Int = 1
                    a.let { it.plus(1) }
                    a.let { that -> that.plus(1) }
                }
            """.trimIndent()
        )
        assertThat(findings).hasSize(2)
        assertThat(findings).allMatch { it.message == MESSAGE_OMIT_LET }
    }

    @Test
    fun `reports unnecessary lets that can be changed to ordinary method call 2`() {
        val findings = subject.compileAndLintWithContext(
            env,
            """
                fun f() {
                    val a: Int? = null
                    a?.let { it.plus(1) }
                    a?.let { that -> that.plus(1) }
                }
            """.trimIndent()
        )
        assertThat(findings).hasSize(2)
        assertThat(findings).allMatch { it.message == MESSAGE_OMIT_LET }
    }

    @Test
    fun `reports unnecessary lets that can be changed to ordinary method call 3`() {
        val findings = subject.compileAndLintWithContext(
            env,
            """
                fun f() {
                    val a: Int? = null
                    a?.let { it?.plus(1) }
                    a?.let { that -> that?.plus(1) }
                }
            """.trimIndent()
        )
        assertThat(findings).hasSize(2)
        assertThat(findings).allMatch { it.message == MESSAGE_OMIT_LET }
    }

    @Test
    fun `reports unnecessary lets that can be changed to ordinary method call 4`() {
        val findings = subject.compileAndLintWithContext(
            env,
            """
                fun f() {
                    val a: Int? = null
                    a?.let { that -> that.plus(1) }?.let { it.plus(1) }
                }
            """.trimIndent()
        )
        assertThat(findings).hasSize(2)
        assertThat(findings).allMatch { it.message == MESSAGE_OMIT_LET }
    }

    @Test
    fun `reports unnecessary lets that can be changed to ordinary method call 5`() {
        val findings = subject.compileAndLintWithContext(
            env,
            """
                fun f() {
                    val a: Int = 1
                    a.let { 1.plus(1) }
                    a.let { that -> 1.plus(1) }
                }
            """.trimIndent()
        )
        assertThat(findings).hasSize(2)
        assertThat(findings).allMatch { it.message == MESSAGE_OMIT_LET }
    }

    @Test
    fun `reports unnecessary lets that can be changed to ordinary method call 6`() {
        val findings = subject.compileAndLintWithContext(
            env,
            """
                fun f() {
                    val a: Int = 1
                    val x = a.let { 1.plus(1) }
                    val y = a.let { that -> 1.plus(1) }
                }
            """.trimIndent()
        )
        assertThat(findings).hasSize(2)
        assertThat(findings).allMatch { it.message == MESSAGE_OMIT_LET }
    }

    @Test
    fun `reports unnecessary lets that can be replaced with an if`() {
        val findings = subject.compileAndLintWithContext(
            env,
            """
                fun f() {
                    val a: Int? = null
                    a?.let { 1.plus(1) }
                    a?.let { that -> 1.plus(1) }
                }
            """.trimIndent()
        )
        assertThat(findings).hasSize(2)
        assertThat(findings).allMatch { it.message == MESSAGE_USE_IF }
    }

    @Test
    fun `reports unnecessary lets that can be changed to ordinary method call 7`() {
        val findings = subject.compileAndLintWithContext(
            env,
            """
                fun f() {
                    val a: Int? = null
                    a.let { print(it) }
                    a.let { that -> print(that) }
                }
            """.trimIndent()
        )
        assertThat(findings).hasSize(2)
        assertThat(findings).allMatch { it.message == MESSAGE_OMIT_LET }
    }

    @Test
    fun `reports use of let without the safe call operator when we use an argument`() {
        val findings = subject.compileAndLintWithContext(
            env,
            """
                fun f() {
                    val f: (Int?) -> Boolean = { true }
                    val a: Int? = null
                    a.let(f)
                }
            """.trimIndent()
        )
        assertThat(findings).hasSize(1)
        assertThat(findings).allMatch { it.message == MESSAGE_OMIT_LET }
    }

    @Test
    fun `does not report lets used for function calls 1`() {
        val findings = subject.compileAndLintWithContext(
            env,
            """
                fun f() {
                    val a: Int? = null
                    a?.let { print(it) }
                    a?.let { that -> 1.plus(that) }
                }
            """.trimIndent()
        )
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report lets used for function calls 2`() {
        val findings = subject.compileAndLintWithContext(
            env,
            """
                fun f() {
                    val a: Int? = null
                    a?.let { that -> 1.plus(that) }?.let { print(it) }
                }
            """.trimIndent()
        )
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report 'can be replaced by if' because you will need an else too`() {
        val findings = subject.compileAndLintWithContext(
            env,
            """
                fun f() {
                    val a: Int? = null
                    val x = a?.let { 1.plus(1) }
                    val y = a?.let { that -> 1.plus(1) }
                }
            """.trimIndent()
        )
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report a let where returned value is used - #2987`() {
        val findings = subject.compileAndLintWithContext(
            env,
            """
                fun f() {
                    val a = listOf<List<String>?>(listOf(""))
                        .map { list -> list?.let { it + it } }
                }
            """.trimIndent()
        )
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report use of let with the safe call operator when we use an argument`() {
        val findings = subject.compileAndLintWithContext(
            env,
            """
                fun f() {
                    val f: (Int?) -> Boolean = { true }
                    val a: Int? = null
                    a?.let(f)
                }
            """.trimIndent()
        )
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `does not report lets with lambda body containing more than one statement`() {
        val findings = subject.compileAndLintWithContext(
            env,
            """
                fun f() {
                    val a: Int? = null
                    val b: Int = 1
                    b.let {
                        it.plus(1)
                        it.plus(2)
                    }
                    a?.let {
                        it.plus(1)
                        it.plus(2)
                    }
                    b.let { that ->
                        that.plus(1)
                        that.plus(2)
                    }
                    a?.let { that ->
                        that.plus(1)
                        that.plus(2)
                    }
                    a?.let { that ->
                        1.plus(that)
                    }
                    ?.let {
                        it.plus(1)
                        it.plus(2)
                    }
                }
            """.trimIndent()
        )
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report lets where it is used multiple times`() {
        val findings = subject.compileAndLintWithContext(
            env,
            """
                fun f() {
                    val a: Int? = null
                    val b: Int = 1
                    a?.let { it.plus(it) }
                    b.let { it.plus(it) }
                    a?.let { foo -> foo.plus(foo) }
                    b.let { foo -> foo.plus(foo) }
                }
            """.trimIndent()
        )
        assertThat(findings).isEmpty()
    }

    @Nested
    inner class `destructuring declarations` {
        @Test
        fun `does not report 'let' when parameters are used more than once`() {
            val content = """
                data class Foo(val a: Int, val b: Int)
                
                fun test(foo: Foo) {
                    foo.let { (a, b) -> a + b }
                }
            """.trimIndent()
            val findings = subject.compileAndLintWithContext(env, content)
            assertThat(findings).isEmpty()
        }

        @Test
        fun `does not report 'let' with a safe call when a parameter is used more than once`() {
            val content = """
                data class Foo(val a: Int, val b: Int)
                
                fun test(foo: Foo) {
                    foo.let { (a, _) -> a + a }
                }
            """.trimIndent()
            val findings = subject.compileAndLintWithContext(env, content)
            assertThat(findings).isEmpty()
        }

        @Test
        fun `does not report 'let' when parameters with types are used more than once`() {
            val content = """
                data class Foo(val a: Int, val b: Int)
                
                fun test(foo: Foo) {
                    foo.let { (a: Int, b: Int) -> a + b }
                }
            """.trimIndent()
            val findings = subject.compileAndLintWithContext(env, content)
            assertThat(findings).isEmpty()
        }

        @Test
        fun `reports 'let' when parameters are used only once`() {
            val content = """
                data class Foo(val a: Int, val b: Int)
                
                fun test(foo: Foo) {
                    foo.let { (a, _) -> a }
                }
            """.trimIndent()
            val findings = subject.compileAndLintWithContext(env, content)
            assertThat(findings).hasSize(1)
            assertThat(findings).allMatch { it.message == MESSAGE_OMIT_LET }
        }

        @Test
        fun `reports 'let' with a safe call when parameters are used only once`() {
            val content = """
                data class Foo(val a: Int, val b: Int)
                
                fun test(foo: Foo?) {
                    foo?.let { (_, b) -> b.plus(1) }
                }
            """.trimIndent()
            val findings = subject.compileAndLintWithContext(env, content)
            assertThat(findings).hasSize(1)
            assertThat(findings).allMatch { it.message == MESSAGE_OMIT_LET }
        }

        @Test
        fun `reports 'let' when parameters are not used`() {
            val content = """
                data class Foo(val a: Int, val b: Int)
                
                fun test(foo: Foo) {
                    foo.let { (_, _) -> 0 }
                }
            """.trimIndent()
            val findings = subject.compileAndLintWithContext(env, content)
            assertThat(findings).hasSize(1)
            assertThat(findings).allMatch { it.message == MESSAGE_OMIT_LET }
        }

        @Test
        fun `reports 'let' with a safe call when parameters are not used`() {
            val content = """
                data class Foo(val a: Int, val b: Int)
                
                fun test(foo: Foo?) {
                    foo?.let { (_, _) -> 0 }
                }
            """.trimIndent()
            val findings = subject.compileAndLintWithContext(env, content)
            assertThat(findings).hasSize(1)
            assertThat(findings).allMatch { it.message == MESSAGE_USE_IF }
        }
    }

    @Test
    fun `reports when implicit parameter isn't used`() {
        val content = """
            fun test(value: Int?) {
                value?.let {
                    listOf(1).map { it }
                }
            }
        """.trimIndent()
        val findings = subject.compileAndLintWithContext(env, content)
        assertThat(findings).hasSize(1)
        assertThat(findings).allMatch { it.message == MESSAGE_USE_IF }
    }

    @Test
    fun `does not report when an implicit parameter is used in an inner lambda`() {
        val content = """
            fun callMe(callback: () -> Unit) {
                callback()
            }
            
            fun test(value: Int?) {
                value?.let {
                    callMe {
                        println(it)
                    }
                }
            }
        """.trimIndent()
        val findings = subject.compileAndLintWithContext(env, content)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report lets with invoke operator calls`() {
        val findings = subject.compileAndLintWithContext(
            env,
            """
                fun f(callback: ((Int) -> Int)?) {
                    callback?.let { that ->
                        that(42)
                    }
                }
            """.trimIndent()
        )
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report let is used as cast-#5701`() {
        val findings = subject.compileAndLintWithContext(
            env,
            """
                import java.lang.reflect.Field
                import java.lang.reflect.Modifier

                fun Field.clearFinal() {
                    // Java 8-17 compatible version of: Field::class.java.getDeclaredField("modifiers")
                    // Idea: https://stackoverflow.com/a/69418150/253468
                    Class::class.java
                        .getDeclaredMethod("getDeclaredFields0", Boolean::class.javaPrimitiveType)
                        .apply { isAccessible = true }
                        .invoke(Field::class.java, false)
                        .let { @Suppress("UNCHECKED_CAST") (it as Array<Field>) }
                        .single { it.name == "modifiers" }
                        // Then clear the final modifier.
                        .apply { isAccessible = true }
                        .setInt(this, this.modifiers and Modifier.FINAL.inv())
                }
            """.trimIndent()
        )
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does report let is used for extension function which can put into chain`() {
        val findings = subject.compileAndLintWithContext(
            env,
            """
                fun <M>List<M>.mutableCast() = this as ArrayList<M>
                fun getList(): List<Int> = arrayListOf()
                fun testFun() {
                    getList().let { 
                        it.mutableCast()
                    }.also { 
                        it.add(1)
                    }.joinToString()
                }
            """.trimIndent()
        )
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `does report let is used for extension function which can put into long chain`() {
        val findings = subject.compileAndLintWithContext(
            env,
            """
                fun <M> List<M>.mutableCast() = this as ArrayList<M>
                fun getList(): List<Int> = arrayListOf()
                fun testFun() {
                    getList().map {
                        it.toString()
                    }.map {
                        it.length
                    }.map {
                        it.toString()
                    }.map {
                        it.length
                    }.let {
                        it.mutableCast()
                    }.also {
                        it.add(1)
                    }.joinToString()
                }
            """.trimIndent()
        )
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `does not report let is used for extension function which can put into long chain`() {
        val findings = subject.compileAndLintWithContext(
            env,
            """
                fun <M> List<M>.mutableCast() = this as ArrayList<M>
                fun getList(): List<Int> = arrayListOf()
                fun testFun() {
                    getList().map {
                        it.toString()
                    }.map {
                        it.length
                    }.map {
                        it.toString()
                    }.map {
                        it.length
                    }.let {
                        if (System.currentTimeMillis() % 2 = 0) {
                            println("System time is even")
                            it.mutableCast()
                        } else {
                            println("Odd time :(")  
                            it.mutableCast()
                        }
                    }.also {
                        it.add(1)
                    }.joinToString()
                }
            """.trimIndent()
        )
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report let is used to call a fun at the end-#4388`() {
        val findings = subject.compileAndLintWithContext(
            env,
            """
                class Context(val resourc: Resou)
                fun test() {
                    val fontMap = context.resources
                    .openRawResource("font")
                    .bufferedReader()
                    .use { it.readText() }
                    .let { parseFontMap(it) }
                }
            """.trimIndent()
        )
        assertThat(findings).isEmpty()
    }
}

private const val MESSAGE_OMIT_LET = "let expression can be omitted"
private const val MESSAGE_USE_IF = "let expression can be replaced with a simple if"
