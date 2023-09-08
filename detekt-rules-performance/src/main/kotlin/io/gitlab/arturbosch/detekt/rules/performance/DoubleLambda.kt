package io.gitlab.arturbosch.detekt.rules.performance

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import io.gitlab.arturbosch.detekt.api.internal.RequiresTypeResolution
import org.jetbrains.kotlin.backend.common.descriptors.isSuspend
import org.jetbrains.kotlin.builtins.functions.FunctionInvokeDescriptor
import org.jetbrains.kotlin.builtins.getReceiverTypeFromFunctionType
import org.jetbrains.kotlin.builtins.isExtensionFunctionType
import org.jetbrains.kotlin.builtins.isSuspendFunctionType
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.SimpleFunctionDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLabeledExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi2ir.deparenthesize
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.model.VarargValueArgument
import org.jetbrains.kotlin.resolve.calls.util.getParameterForArgument
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.lazy.descriptors.LazyPackageDescriptor
import org.jetbrains.kotlin.types.KotlinType

/**
 * Avoid creating an unnecessary lambda to call another lambda with the same signature. This incurs
 * a performance penalty compared to directly using the lambda variable.
 *
 * <noncompliant>
 * fun test(lambda: () -> Unit) {
 *     thread {
 *         lambda()
 *     }.start()
 * }
 *
 * fun test(lambda: (Int) -> String) {
 *     val a: (Int) -> String = { lambda(it) }
 * }
 * </noncompliant>
 *
 * <compliant>
 * fun test(lambda: () -> Unit) {
 *     thread(block = lambda).start()
 * }
 *
 * fun test(lambda: (Int) -> String) {
 *     val a: (Int) -> String = lambda
 * }
 * </compliant>
 *
 *
 */
// Implementation taken from https://github.com/JetBrains/intellij-community/blob/master/plugins/kotlin/idea/src/org/jetbrains/kotlin/idea/intentions/ConvertLambdaToReferenceIntention.kt
@RequiresTypeResolution
class DoubleLambda(config: Config = Config.empty) : Rule(config) {
    override val issue: Issue = Issue(
        "DoubleLambda",
        Severity.Performance,
        "Detects unnecessary lambda creation which can be avoided by directly using " +
            "lambda reference",
        Debt.FIVE_MINS
    )

    @Suppress("ReturnCount")
    override fun visitLambdaExpression(lambdaExpression: KtLambdaExpression) {
        super.visitLambdaExpression(lambdaExpression)
        val singleStatement = lambdaExpression.singleStatementOrNull()?.deparenthesize() ?: return
        if (checkInlinedLambda(lambdaExpression)) return
        val (isViolation, errorExpression) = when (singleStatement) {
            is KtCallExpression -> {
                isConvertibleCallInLambda(
                    callableExpression = singleStatement,
                    lambdaExpression = lambdaExpression
                ) to singleStatement
            }

            is KtDotQualifiedExpression -> {
                val selector = singleStatement.selectorExpression ?: return
                if (selector !is KtCallExpression) return
                isConvertibleCallInLambda(
                    callableExpression = selector,
                    explicitReceiver = singleStatement.receiverExpression,
                    lambdaExpression = lambdaExpression
                ) to singleStatement
            }

            else -> false to null
        }

        if (isViolation && errorExpression != null) {
            report(
                CodeSmell(
                    issue,
                    Entity.from(errorExpression),
                    "Use passed lambda ${(errorExpression as KtExpression).text} directly " +
                        "instead of calling it within new lambda. This incurs a performance penalty."
                )
            )
        }
    }

    private fun checkInlinedLambda(lambdaExpression: KtLambdaExpression): Boolean {
        val lambdaParent = lambdaExpression.parent
        val lambdaGrandParent = lambdaExpression.parent.parent
        if (lambdaGrandParent is KtCallExpression && lambdaParent is KtLambdaArgument) {
            val resolvedCall = lambdaGrandParent.getResolvedCall(bindingContext) ?: return true
            val callableDescriptor =
                resolvedCall.resultingDescriptor as? FunctionDescriptor ?: return true
            val lambdaParameter = resolvedCall.getParameterForArgument(lambdaParent) ?: return true
            if (callableDescriptor.isInline && !lambdaParameter.isNoinline) {
                return true
            }
        }
        return false
    }

    private fun CallableMemberDescriptor.overloadedFunctions(): Collection<SimpleFunctionDescriptor> {
        val memberScope = when (val containingDeclaration = this.containingDeclaration) {
            is ClassDescriptor -> containingDeclaration.unsubstitutedMemberScope
            is LazyPackageDescriptor -> containingDeclaration.getMemberScope()
            else -> null
        }
        return memberScope?.getContributedFunctions(name, NoLookupLocation.FROM_IDE).orEmpty()
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")
    private fun isConvertibleCallInLambda(
        callableExpression: KtCallExpression,
        explicitReceiver: KtExpression? = null,
        lambdaExpression: KtLambdaExpression,
    ): Boolean {
        val languageVersionSettings = compilerResources?.languageVersionSettings ?: return false
        val calleeReferenceExpression = when (callableExpression) {
            is KtCallExpression -> callableExpression.calleeExpression as? KtNameReferenceExpression
                ?: return false

//            is KtNameReferenceExpression -> callableExpression
            else -> return false
        }

        val calleeDescriptor =
            calleeReferenceExpression.getResolvedCall(bindingContext)?.resultingDescriptor as? CallableMemberDescriptor
                ?: return false

        if (calleeDescriptor !is FunctionInvokeDescriptor &&
            calleeDescriptor.original.overriddenDescriptors.all { it !is FunctionInvokeDescriptor }
        ) {
            // not a lambda or lambda derived method
            return false
        }

        val isUsingExplicitInvoke = calleeReferenceExpression.text == "invoke"

        val lambdaParameterType = lambdaExpression.lambdaParameterType(bindingContext)
        if (lambdaParameterType?.isExtensionFunctionType == true &&
            lambdaParameterType.getReceiverTypeFromFunctionType() != calleeDescriptor.eligibleReceiverType()
        ) {
            return false
        }

        val lambdaParameterIsSuspend = lambdaParameterType?.isSuspendFunctionType == true
        val calleeFunctionIsSuspend = calleeDescriptor.isSuspend
        if (!lambdaParameterIsSuspend && calleeFunctionIsSuspend) return false
        if (lambdaParameterIsSuspend && !calleeFunctionIsSuspend &&
            !languageVersionSettings.supportsFeature(LanguageFeature.SuspendConversion)
        ) {
            return false
        }

        // No references with type parameters
        if (!checkTypeParameter(calleeDescriptor)) return false

        if (!lambdaExpression.isArgument() &&
            calleeDescriptor.overloadedFunctions().size > 1
        ) {
            val property = lambdaExpression.getStrictParentOfType<KtProperty>()
            if (property != null && property.initializer?.deparenthesize() != lambdaExpression) return false
            val lambdaReturnType =
                bindingContext[BindingContext.EXPRESSION_TYPE_INFO, lambdaExpression]
                    ?.type
                    ?.arguments
                    ?.lastOrNull()
                    ?.type
            if (lambdaReturnType != calleeDescriptor.returnType) return false
        }
        val lambdaFunctionDescriptor =
            bindingContext[BindingContext.FUNCTION, lambdaExpression.functionLiteral]
                ?: return false
        val lambdaValueParameterDescriptors = lambdaFunctionDescriptor.valueParameters
        if (explicitReceiver != null && explicitReceiver !is KtSimpleNameExpression &&
            explicitReceiver.anyDescendantOfType<KtSimpleNameExpression> {
                it.getResolvedCall(bindingContext)?.resultingDescriptor in lambdaValueParameterDescriptors
            }
        ) {
            return false
        }

        val explicitReceiverDescriptor =
            (explicitReceiver as? KtNameReferenceExpression)?.let {
                bindingContext[BindingContext.REFERENCE_TARGET, it]
            }
        val lambdaParameterAsExplicitReceiver = explicitReceiverDescriptor != null &&
            explicitReceiverDescriptor == (if (lambdaFunctionDescriptor.extensionReceiverParameter != null) lambdaFunctionDescriptor else lambdaValueParameterDescriptors.firstOrNull())

        val explicitReceiverShift = if (lambdaParameterAsExplicitReceiver) 1 else 0

        if (callableExpression.valueArguments.count { it.getArgumentExpression() is KtThisExpression } > 1) {
            // usage of two `this`
            return false
        }

        val callableArgumentsCountExcludingThis =
            callableExpression.valueArguments.dropWhile { it.getArgumentExpression() is KtThisExpression }.size
        val lambdaReceiverParameterDescriptor = lambdaFunctionDescriptor.extensionReceiverParameter
        val lambdaReceiverAndParametersCount =
            lambdaValueParameterDescriptors.size + if (lambdaReceiverParameterDescriptor == null) 0 else 1
        if (lambdaParameterAsExplicitReceiver) {
            if (lambdaReceiverAndParametersCount != callableArgumentsCountExcludingThis + explicitReceiverShift) return false
        } else {
            if (lambdaValueParameterDescriptors.size != callableArgumentsCountExcludingThis) return false
        }


        // Same lambda / references function parameter order
        return if (callableExpression is KtCallExpression) {
//            if (lambdaParametersCount < explicitReceiverShift + callableExpression.valueArguments.size) {
//                return false
//            }
            val callableExpressionResolvedCall =
                callableExpression.getResolvedCall(bindingContext) ?: return false
            if (lambdaReceiverAndParametersCount != callableExpressionResolvedCall.valueArguments.size + if (callableExpressionResolvedCall.extensionReceiver == null) 0 else 1) {
                return false
            }
            var thisUsed = false
            val entries = callableExpressionResolvedCall.valueArguments.entries.map {
                it.key to it.value
            }.toMutableList()
//            if (explicitReceiver != null) {
//                entries.add(explicitReceiverDescriptor to explicitReceiverDescriptor)
//            }
            entries.forEach { (callableExpressionValueParameter, callableExpressionResolvedArgument) ->
                val callableExpressionArgument =
                    callableExpressionResolvedArgument.arguments.singleOrNull() ?: return false
                if (callableExpressionResolvedArgument is VarargValueArgument && callableExpressionArgument.getSpreadElement() == null) return false
                val argumentExpression =
                    callableExpressionArgument.getArgumentExpression() as? KtReferenceExpression
                if (!lambdaParameterAsExplicitReceiver && callableExpressionValueParameter.index == 0 && callableExpressionArgument.getArgumentExpression() is KtThisExpression) {
                    thisUsed = true
                    return@forEach
                } else if (lambdaParameterAsExplicitReceiver) {
                    thisUsed = true
                }
                val argumentTarget =
                    bindingContext[BindingContext.REFERENCE_TARGET, argumentExpression] as? ValueParameterDescriptor
                        ?: return false
                if (argumentTarget != lambdaValueParameterDescriptors[callableExpressionValueParameter.index + if (thisUsed) 1 else 0]) {
                    return false
                }
            }
            true
        } else {
            false
        }
    }

    private fun checkTypeParameter(
        calleeDescriptor: CallableMemberDescriptor,
    ): Boolean {
        // we will ignore lambdas with type parameters as bringing that code from intellij-community
        // will require lots of code related to `InsertExplicitTypeArgumentsIntention`, `analyzeAsReplacement`
        // and `diagnostics`
        return calleeDescriptor.typeParameters.isEmpty()
    }

    private fun CallableDescriptor.eligibleReceiverType(): KotlinType? {
        return this.extensionReceiverParameter?.type ?: this.valueParameters.firstOrNull()?.type
    }

    @Suppress("ReturnCount")
    private fun KtLambdaExpression.lambdaParameterType(context: BindingContext): KotlinType? {
        val argument = parentValueArgument() ?: return null
        val callExpression = argument.getStrictParentOfType<KtCallExpression>() ?: return null
        return callExpression
            .getResolvedCall(context)
            ?.getParameterForArgument(argument)
            ?.type
    }

    private fun KtLambdaExpression.parentValueArgument(): KtValueArgument? {
        return if (parent is KtLabeledExpression) {
            parent.parent
        } else {
            parent
        } as? KtValueArgument
    }

    private fun KtLambdaExpression.singleStatementOrNull() =
        bodyExpression?.statements?.singleOrNull()

    private fun KtLambdaExpression.isArgument() =
        this === getStrictParentOfType<KtValueArgument>()?.getArgumentExpression()?.deparenthesize()
}
