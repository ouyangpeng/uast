package org.jetbrains.uast

import com.intellij.psi.PsiAnonymousClass
import com.intellij.psi.PsiClass
import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.visitor.UastTypedVisitor
import org.jetbrains.uast.visitor.UastVisitor
import org.jetbrains.uast.internal.log

/**
 * A class wrapper to be used in [UastVisitor].
 */
interface UClass : UDeclaration, PsiClass {
    override val psi: PsiClass

    /**
     * Returns a [UClass] wrapper of the superclass of this class, or null if this class is [java.lang.Object].
     */
    val uastSuperClass: UClass?
        get() {
            val superClass = superClass ?: return null
            return getUastContext().convertWithParent(superClass)
        }

    /**
     * Returns [UDeclaration] wrappers for the class declarations.
     */
    val uastDeclarations: List<UDeclaration>
    
    val uastFields: List<UVariable>
    val uastInitializers: List<UClassInitializer>
    val uastMethods: List<UMethod>
    val uastNestedClasses: List<UClass>

    override fun asOwnLogString() = "UClass (name = $name)"

    override fun asLogString() = log(asOwnLogString(), annotations, uastDeclarations)

    override fun accept(visitor: UastVisitor) {
        if (visitor.visitClass(this)) return
        annotations.acceptList(visitor)
        uastDeclarations.acceptList(visitor)
        visitor.afterVisitClass(this)
    }

    override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D) =
            visitor.visitClass(this, data)
}

interface UAnonymousClass : UClass, PsiAnonymousClass {
    override val psi: PsiAnonymousClass
}