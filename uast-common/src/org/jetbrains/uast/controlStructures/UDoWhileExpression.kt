/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.uast

import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.internal.log
import org.jetbrains.uast.visitor.UastTypedVisitor
import org.jetbrains.uast.visitor.UastVisitor

/**
 * Represent a
 *
 * `do {
 *      // body
 * } while (expr)`
 *
 * loop expression.
 */
interface UDoWhileExpression : ULoopExpression {
    /**
     * Returns the loop post-condition.
     */
    val condition: UExpression

    /**
     * Returns an identifier for the 'do' keyword.
     */
    val doIdentifier: UIdentifier

    /**
     * Returns an identifier for the 'while' keyword.
     */
    val whileIdentifier: UIdentifier

    override fun accept(visitor: UastVisitor) {
        if (visitor.visitDoWhileExpression(this)) return
        annotations.acceptList(visitor)
        condition.accept(visitor)
        body.accept(visitor)
        visitor.afterVisitDoWhileExpression(this)
    }

    override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D) =
            visitor.visitDoWhileExpression(this, data)

    override fun asRenderString() = buildString {
        append("do ")
        append(body.asRenderString())
        appendln("while (${condition.asRenderString()})")
    }

    override fun asLogString() = log()
}
