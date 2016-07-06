package org.jetbrains.uast

import com.intellij.psi.*
import kotlin.properties.Delegates

/**
 * Interface for the Uast element converter.
 * Each [UastLanguagePlugin] should implement a proper [UastContext],
 *  which translates language-specific AST elements to Uast elements.
 */
interface UastConverter {
//    /**
//     * Convert[element] to the [UElement] without the parent.
//     * The [UElement.parent] value will be `null`.
//     */
//    fun convertWithoutParent(element: Any?): UElement?
}

abstract class UastLanguagePlugin {
    open lateinit var context: UastContext

    /**
     * Checks if the file with the given [fileName] is supported.
     *
     * @param fileName the source file name.
     * @return true, if the file is supported by this converter, false otherwise.
     */
    abstract fun isFileSupported(fileName: String): Boolean
    
    /**
     * Returns the converter priority. Might be positive, negative or 0 (Java's is 0).
     * UastConverter with the higher priority will be queried earlier.
     *
     * Priority is useful when a language N wraps its own elements (NElement) to, for example, Java's PsiElements,
     *  and Java resolves the reference to such wrapped PsiElements, not the original NElement.
     * In this case N implementation can handle such wrappers in UastConverter earlier than Java's converter,
     *  so N language converter will have a higher priority.
     */
    abstract val priority: Int

    abstract fun convert(element: Any?, parent: UElement?): UElement?

    /**
     * Convert [element] to the [UElement] with the given parent.
     */
    abstract fun convertWithParent(element: Any?): UElement?

    abstract fun getMethodCallExpression(
            e: PsiElement, 
            containingClassFqName: String?, 
            methodName: String
    ): Pair<UCallExpression, PsiMethod>?

    abstract fun getConstructorCallExpression(
            e: PsiElement,
            fqName: String
    ) : Triple<UCallExpression, PsiMethod, PsiClass>?

    abstract fun getMethodBody(e: PsiMethod): UExpression?
    
    abstract fun getInitializerBody(e: PsiClassInitializer): UExpression?

    abstract fun getInitializerBody(e: PsiVariable): UExpression?
}