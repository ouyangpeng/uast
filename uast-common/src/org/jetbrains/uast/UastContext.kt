package org.jetbrains.uast

import com.intellij.lang.Language
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.psi.*

/**
 * Manages the UAST to PSI conversion.
 */
class UastContext(val project: Project) : UastLanguagePlugin {
    private companion object {
        private val CONTEXT_LANGUAGE = object : Language("UastContextLanguage") {}
    }

    override val language: Language
        get() = CONTEXT_LANGUAGE

    override val priority: Int
        get() = 0

    val languagePlugins: Collection<UastLanguagePlugin>
        get() = UastLanguagePlugin.getInstances()

    fun findPlugin(element: PsiElement): UastLanguagePlugin? {
        val language = element.language
        return languagePlugins.firstOrNull { it.language == language }
    }

    override fun isFileSupported(fileName: String) = languagePlugins.any { it.isFileSupported(fileName) }

    fun getMethod(method: PsiMethod): UMethod = convertWithParent<UMethod>(method)!!

    fun getVariable(variable: PsiVariable): UVariable = convertWithParent<UVariable>(variable)!!

    fun getClass(clazz: PsiClass): UClass = convertWithParent<UClass>(clazz)!!

    override fun convertElement(element: PsiElement, parent: UElement?, requiredType: Class<out UElement>?): UElement? {
        return findPlugin(element)?.convertElement(element, parent, requiredType)
    }

    override fun convertElementWithParent(element: PsiElement, requiredType: Class<out UElement>?): UElement? {
        return findPlugin(element)?.convertElementWithParent(element, requiredType)
    }

    override fun getMethodCallExpression(
            element: PsiElement,
            containingClassFqName: String?,
            methodName: String
    ): UastLanguagePlugin.ResolvedMethod? {
        return findPlugin(element)?.getMethodCallExpression(element, containingClassFqName, methodName)
    }

    override fun getConstructorCallExpression(
            element: PsiElement,
            fqName: String
    ): UastLanguagePlugin.ResolvedConstructor? {
        return findPlugin(element)?.getConstructorCallExpression(element, fqName)
    }

    override fun isExpressionValueUsed(element: UExpression): Boolean {
        val language = element.getLanguage()
        return (languagePlugins.firstOrNull { it.language == language })?.isExpressionValueUsed(element) ?: false
    }

    private tailrec fun UElement.getLanguage(): Language {
        psi?.language?.let { return it }
        val containingElement = this.uastParent ?: throw IllegalStateException("At least UFile should have a language")
        return containingElement.getLanguage()
    }
}

/**
 * Converts the element along with its parents to UAST.
 */
fun PsiElement?.toUElement() =
        this?.let { ServiceManager.getService(project, UastContext::class.java).convertElementWithParent(this, null) }

/**
 * Converts the element to an UAST element of the given type. Returns null if the PSI element type does not correspond
 * to the given UAST element type.
 */
fun <T : UElement> PsiElement?.toUElement(cls: Class<out T>): T? =
        this?.let { ServiceManager.getService(project, UastContext::class.java).convertElementWithParent(this, cls) as T? }

inline fun <reified T : UElement> PsiElement?.toUElementOfType(): T? =
        this?.let { ServiceManager.getService(project, UastContext::class.java).convertElementWithParent(this, T::class.java) as T? }

/**
 * Finds an UAST element of a given type at the given [offset] in the specified file. Returns null if there is no UAST
 * element of the given type at the given offset.
 */
fun <T : UElement> PsiFile.findUElementAt(offset: Int, cls: Class<out T>): T? {
    val element = findElementAt(offset) ?: return null
    val uElement = element.toUElement() ?: return null
    @Suppress("UNCHECKED_CAST")
    return uElement.withContainingElements.firstOrNull { cls.isInstance(it) } as T?
}

/**
 * Finds an UAST element of the given type among the parents of the given PSI element.
 */
@JvmOverloads
fun <T : UElement> PsiElement?.getUastParentOfType(cls: Class<out T>, strict: Boolean = false): T? {
    val uElement = this.toUElement() ?: return null
    val sequence = if (strict)
        (uElement.uastParent?.withContainingElements ?: emptySequence())
    else
        uElement.withContainingElements

    @Suppress("UNCHECKED_CAST")
    return sequence.firstOrNull { cls.isInstance(it) } as T?
}

inline fun <reified T : UElement> PsiElement?.getUastParentOfType(strict: Boolean = false): T? = getUastParentOfType(T::class.java, strict)
