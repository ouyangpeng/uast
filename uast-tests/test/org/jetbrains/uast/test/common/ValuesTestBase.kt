package org.jetbrains.uast.test.common

import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UFile
import org.jetbrains.uast.evaluation.UEvaluationContext
import org.jetbrains.uast.evaluation.analyzeAll
import org.jetbrains.uast.test.env.assertEqualsToFile
import org.jetbrains.uast.visitor.UastVisitor
import java.io.File

interface ValuesTestBase {
    fun getValuesFile(testName: String): File

    private fun UFile.asLogValues(): String {
        val evaluationContext = analyzeAll()
        return ValueLogger(evaluationContext).apply {
            this@asLogValues.accept(this)
        }.toString()
    }

    fun check(testName: String, file: UFile) {
        val valuesFile = getValuesFile(testName)

        assertEqualsToFile("Log values", valuesFile, file.asLogValues())
    }

    class ValueLogger(val evaluationContext: UEvaluationContext) : UastVisitor {

        val builder = StringBuilder()

        var level = 0

        override fun visitElement(node: UElement): Boolean {
            val initialLine = node.asLogString() + " [" + run {
                val renderString = node.asRenderString().lines()
                if (renderString.size == 1) {
                    renderString.single()
                } else {
                    renderString.first() + "..." + renderString.last()
                }
            } + "]"

            (1..level).forEach { builder.append("    ") }
            builder.append(initialLine)
            if (node is UExpression) {
                val value = evaluationContext.valueOf(node)
                builder.append(" = ").append(value)
            }
            builder.appendln()
            level++
            return false
        }

        override fun afterVisitElement(node: UElement) {
            level--
        }

        override fun toString() = builder.toString()
    }
}