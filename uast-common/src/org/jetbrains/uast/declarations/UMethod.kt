package org.jetbrains.uast

import com.intellij.psi.PsiAnnotationMethod
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.internal.log
import org.jetbrains.uast.visitor.UastTypedVisitor
import org.jetbrains.uast.visitor.UastVisitor

/**
 * A method visitor to be used in [UastVisitor].
 */
interface UMethod : UDeclaration, PsiMethod {
    override val psi: PsiMethod

    /**
     * Returns the body expression (which can be also a [UBlockExpression]).
     */
    val uastBody: UExpression?

    /**
     * Returns the method parameters.
     */
    val uastParameters: List<UParameter>

    /**
     * Returns true, if the method overrides a method of a super class.
     */
    val isOverride: Boolean

    @Deprecated("Use uastBody instead.", ReplaceWith("uastBody"))
    override fun getBody() = psi.body

    override fun accept(visitor: UastVisitor) {
        if (visitor.visitMethod(this)) return
        annotations.acceptList(visitor)
        uastParameters.acceptList(visitor)
        uastBody?.accept(visitor)
        visitor.afterVisitMethod(this)
    }

    override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D) =
            visitor.visitMethod(this, data)

    override fun asOwnLogString() = "UMethod (name = $name)"

    override fun asLogString() = log(asOwnLogString(), annotations, uastParameters, uastBody)
}

interface UAnnotationMethod : UMethod, PsiAnnotationMethod {
    override val psi: PsiAnnotationMethod

    /**
     * Returns the default value of this annotation method.
     */
    val uastDefaultValue: UExpression?

    override fun getDefaultValue() = psi.defaultValue

    override fun accept(visitor: UastVisitor) {
        if (visitor.visitMethod(this)) return
        annotations.acceptList(visitor)
        uastParameters.acceptList(visitor)
        uastBody?.accept(visitor)
        uastDefaultValue?.accept(visitor)
        visitor.afterVisitMethod(this)
    }

    override fun asOwnLogString() = "UAnnotationMethod (name = $name)"
}