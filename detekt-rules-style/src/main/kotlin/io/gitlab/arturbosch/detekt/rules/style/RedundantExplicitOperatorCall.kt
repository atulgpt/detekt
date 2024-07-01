package io.gitlab.arturbosch.detekt.rules.style

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.RequiresTypeResolution
import io.gitlab.arturbosch.detekt.api.Rule
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.util.isValidOperator

/**
 * In Kotlin functions operators like `get` or `set`(marked with `operator fun`) can be replaced
 * with the shorter operator — `[]`,
 * see [operator-overloading](https://kotlinlang.org/docs/operator-overloading.html).
 * Prefer the usage of the symbolic representation of operators instead of named function call.
 *
 * <noncompliant>
 *  val map = mutableMapOf<String, String>()
 *  map.put("key", "value")
 *  val value = map.get("key")
 *
 *  val a = 0
 *  val b = a.inc()
 *  val c = a.dec()
 * </noncompliant>
 *
 * <compliant>
 *  val map = mutableMapOf<String, String>()
 *  map["key"] = "value"
 *  val value = map["key"]
 *
 *  val a = 0
 *  val b = a++
 *  val c = ++a
 * </compliant>
 */
@RequiresTypeResolution
class RedundantExplicitOperatorCall(config: Config) : Rule(
    config,
    "Prefer usage of operator symbols instead of calling the function explicitly."
) {

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        val callDescriptor =
            expression.getResolvedCall(bindingContext)?.resultingDescriptor as? FunctionDescriptor
                ?: return
        if (callDescriptor.isValidOperator() && expression.calleeExpression?.text?.isNotBlank() == true) {
            report(CodeSmell(Entity.from(expression), description))
        }
    }
}
