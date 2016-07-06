/*
 * Copyright 2010-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.uast.kotlin

import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.kotlin.codegen.state.KotlinTypeMapper
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.uast.kotlin.expressions.KotlinUBreakExpression
import org.jetbrains.uast.kotlin.expressions.KotlinUContinueExpression
import org.jetbrains.uast.kotlin.kinds.KotlinSpecialExpressionKinds
import org.jetbrains.uast.*
import org.jetbrains.uast.kotlin.psi.UastKotlinPsiParameter
import org.jetbrains.uast.kotlin.psi.UastKotlinPsiVariable
import org.jetbrains.uast.psi.PsiElementBacked

interface KotlinUastBindingContextProviderService {
    fun getBindingContext(project: Project, element: KtElement?): BindingContext
    fun getTypeMapper(project: Project, element: KtElement?): KotlinTypeMapper?
}

class KotlinUastLanguagePlugin : UastLanguagePlugin() {
    override fun getMethodCallExpression(
            e: PsiElement, 
            containingClassFqName: String?,
            methodName: String
    ): Pair<UCallExpression, PsiMethod>? {
        TODO()
    }

    override fun getConstructorCallExpression(
            e: PsiElement, 
            fqName: String
    ): Triple<UCallExpression, PsiMethod, PsiClass>? {
        TODO()
    }

    override fun getMethodBody(e: PsiMethod): UExpression {
        TODO()
    }

    override fun getInitializerBody(e: PsiVariable): UExpression? {
        TODO()
    }

    override fun getInitializerBody(e: PsiClassInitializer): UExpression? {
        TODO()
    }

    override fun isFileSupported(fileName: String): Boolean {
        return fileName.endsWith(".kt", false) || fileName.endsWith(".kts", false)
    }

    override val priority: Int
        get() = 10

    override fun convert(element: Any?, parent: UElement?): UElement? {
        if (element !is KtElement) return null
        return KotlinConverter.convertKtElement(element, parent)
    }

//    override fun convertWithoutParent(element: Any?): UElement? {
//        if (element !is KtElement) return null
//        return KotlinConverter.convertKtElement(element, null)
//    }

    override fun convertWithParent(element: Any?): UElement? {
        if (element !is KtElement) return null

        val parent = element.parent ?: return null
        val parentUElement = convertWithParent(parent) ?: return null
        return KotlinConverter.convertKtElement(element, parentUElement)
    }
}

internal object KotlinConverter : UastConverter {
    internal fun convertKtElement(element: KtElement?, parent: UElement?): UElement? = when (element) {
        is KtParameterList -> KotlinUVariableDeclarationsExpression(parent).apply {
            val languagePlugin = parent!!.getLanguagePlugin()
            variables = element.parameters.mapIndexed { i, p -> 
                SimpleUVariable.create(UastKotlinPsiParameter.create(p, element, i), languagePlugin, this)
            }
        }
        is KtClassBody -> KotlinUExpressionList(element, KotlinSpecialExpressionKinds.CLASS_BODY, parent).apply {
            expressions = emptyList()
        }
        is KtCatchClause -> KotlinUCatchClause(element, parent)
        is KtExpression -> KotlinConverter.convertExpression(element, parent)
        else -> {
            if (element is LeafPsiElement && element.elementType == KtTokens.IDENTIFIER) {
                asSimpleReference(element, parent)
            } else {
                null
            }
        }
    }
    
    private fun convertVariablesDeclaration(
            psi: KtVariableDeclaration, 
            parent: UElement?
    ): UVariableDeclarationsExpression {
        val languagePlugin = parent!!.getLanguagePlugin()
        val parentPsiElement = (parent as? PsiElementBacked)?.psi
        val variable = SimpleUVariable.create(UastKotlinPsiVariable.create(psi, parentPsiElement), languagePlugin, parent)
        return KotlinUVariableDeclarationsExpression(parent).apply { variables = listOf(variable) }
    }
    
    private fun convertStringTemplateExpression(
            expression: KtStringTemplateExpression,
            parent: UElement?,
            i: Int
    ): UExpression {
        return if (i == 1) KotlinStringTemplateUBinaryExpression(expression, parent).apply {
            leftOperand = convert(expression.entries[0], this)
            rightOperand = convert(expression.entries[1], this)
        } else KotlinStringTemplateUBinaryExpression(expression, parent).apply {
            leftOperand = convertStringTemplateExpression(expression, parent, i - 1)
            rightOperand = convert(expression.entries[i], this)
        }
    }

    internal fun convert(entry: KtStringTemplateEntry, parent: UElement?): UExpression = when (entry) {
        is KtStringTemplateEntryWithExpression -> convertOrEmpty(entry.expression, parent)
        is KtEscapeStringTemplateEntry -> KotlinStringULiteralExpression(entry, parent, entry.unescapedValue)
        else -> {
            KotlinStringULiteralExpression(entry, parent)
        }
    }

    internal fun convertExpression(expression: KtExpression, parent: UElement?): UExpression = when (expression) {
        is KtVariableDeclaration -> convertVariablesDeclaration(expression, parent)

        is KtStringTemplateExpression -> {
            if (expression.entries.isEmpty())
                KotlinStringULiteralExpression(expression, parent, "")
            else if (expression.entries.size == 1)
                convert(expression.entries[0], parent)
            else
                convertStringTemplateExpression(expression, parent, expression.entries.size - 1)
        }
        is KtDestructuringDeclaration -> KotlinUVariableDeclarationsExpression(parent).apply {
            val languagePlugin = parent!!.getLanguagePlugin()
            val tempAssignment = SimpleUVariable.create(UastKotlinPsiVariable.create(expression), languagePlugin, parent)
            val destructuringAssignments = expression.entries.mapIndexed { i, entry ->
                val psiFactory = KtPsiFactory(expression.project)
                val initializer = psiFactory.createExpression("${tempAssignment.name}.component${i + 1}()")
                SimpleUVariable.create(UastKotlinPsiVariable.create(
                        entry, tempAssignment.psi, initializer), languagePlugin, parent) 
            }
            variables = listOf(tempAssignment) + destructuringAssignments
        }
        is KtLabeledExpression -> KotlinULabeledExpression(expression, parent)
        is KtClassLiteralExpression -> KotlinUClassLiteralExpression(expression, parent)
        is KtObjectLiteralExpression -> KotlinUObjectLiteralExpression(expression, parent)
        is KtStringTemplateEntry -> convertOrEmpty(expression.expression, parent)
        is KtDotQualifiedExpression -> KotlinUQualifiedReferenceExpression(expression, parent)
        is KtSafeQualifiedExpression -> KotlinUSafeQualifiedExpression(expression, parent)
        is KtSimpleNameExpression -> KotlinUSimpleReferenceExpression(expression, expression.getReferencedName(), parent)
        is KtCallExpression -> KotlinUFunctionCallExpression(expression, parent)
        is KtBinaryExpression -> KotlinUBinaryExpression(expression, parent)
        is KtParenthesizedExpression -> KotlinUParenthesizedExpression(expression, parent)
        is KtPrefixExpression -> KotlinUPrefixExpression(expression, parent)
        is KtPostfixExpression -> KotlinUPostfixExpression(expression, parent)
        is KtThisExpression -> KotlinUThisExpression(expression, parent)
        is KtSuperExpression -> KotlinUSuperExpression(expression, parent)
        is KtCallableReferenceExpression -> KotlinUCallableReferenceExpression(expression, parent)
        is KtIsExpression -> KotlinUTypeCheckExpression(expression, parent)
        is KtIfExpression -> KotlinUIfExpression(expression, parent)
        is KtWhileExpression -> KotlinUWhileExpression(expression, parent)
        is KtDoWhileExpression -> KotlinUDoWhileExpression(expression, parent)
        is KtForExpression -> KotlinUForEachExpression(expression, parent)
        is KtWhenExpression -> KotlinUSwitchExpression(expression, parent)
        is KtBreakExpression -> KotlinUBreakExpression(expression, parent)
        is KtContinueExpression -> KotlinUContinueExpression(expression, parent)
        is KtReturnExpression -> KotlinUReturnExpression(expression, parent)
        is KtThrowExpression -> KotlinUThrowExpression(expression, parent)
        is KtBlockExpression -> KotlinUBlockExpression(expression, parent)
        is KtConstantExpression -> KotlinULiteralExpression(expression, parent)
        is KtTryExpression -> KotlinUTryExpression(expression, parent)
        is KtArrayAccessExpression -> KotlinUArrayAccessExpression(expression, parent)
        is KtLambdaExpression -> KotlinULambdaExpression(expression, parent)
        is KtBinaryExpressionWithTypeRHS -> KotlinUBinaryExpressionWithType(expression, parent)

        else -> UnknownKotlinExpression(expression, parent)
    }

    internal fun asSimpleReference(element: PsiElement?, parent: UElement?): USimpleNameReferenceExpression? {
        if (element == null) return null
        return KotlinNameUSimpleReferenceExpression(element, KtPsiUtil.unquoteIdentifier(element.text), parent)
    }

    internal fun convertOrEmpty(expression: KtExpression?, parent: UElement?): UExpression {
        return if (expression != null) convertExpression(expression, parent) else UastEmptyExpression
    }

    internal fun convertOrNull(expression: KtExpression?, parent: UElement?): UExpression? {
        return if (expression != null) convertExpression(expression, parent) else null
    }
}