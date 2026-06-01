package org.hql.query.ast

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.hql.ExprLexer
import org.hql.ExprParser
import org.hql.query.BooleanCell
import org.hql.query.FloatCell
import org.hql.query.IntCell
import org.hql.query.NullCell
import org.hql.query.StringCell
import org.hql.query.expressions.Expression

// Рекурсивная функция для рисования дерева логики
private fun printTree(expr: Expression, indent: String, out: Appendable = System.out) {
    when (expr) {
        is Expression.Field ->
            out.appendLine("$indent[FIELD=${expr.field}]")
        is Expression.Literal ->
            out.appendLine("$indent[LITERAL=${expr.value}]")

        is Expression.And -> {
            out.appendLine("$indent[AND]")
            printTree(expr.left, "$indent  |", out = out)
            printTree(expr.right, "$indent  |", out = out)
        }
        is Expression.Or -> {
            out.appendLine("$indent[OR]")
            printTree(expr.left, "$indent  |", out = out)
            printTree(expr.right, "$indent  |", out = out)
        }
        is Expression.Comparison -> {
            out.appendLine("$indent[COMPARISON ${expr.op}]")
            printTree(expr.left, "$indent  |", out = out)
            printTree(expr.right, "$indent  |", out = out)
        }

        is Expression.Access -> {
            out.appendLine("$indent[ACCESS ${expr.field}]")
            printTree(expr.expr, "$indent  |", out = out)
        }
        is Expression.Div -> {
            out.appendLine("$indent[DIV]")
            printTree(expr.left, "$indent  |", out = out)
            printTree(expr.right, "$indent  |", out = out)
        }
        is Expression.Minus -> {
            out.appendLine("$indent[MINUS]")
            printTree(expr.left, "$indent  |", out = out)
            printTree(expr.right, "$indent  |", out = out)
        }
        is Expression.Mult -> {
            out.appendLine("$indent[MULT]")
            printTree(expr.left, "$indent  |", out = out)
            printTree(expr.right, "$indent  |", out = out)
        }
        is Expression.Plus -> {
            out.appendLine("$indent[PLUS]")
            printTree(expr.left, "$indent  |", out = out)
            printTree(expr.right, "$indent  |", out = out)
        }
        is Expression.FunctionCall -> {
            out.appendLine("$indent[FUNCTION_CALL=${expr.name}]")
            expr.args.forEach { printTree(it, "$indent  |", out = out) }
        }
    }
}

sealed class DataSource {
    data class Class(val name: String) : DataSource() {
        override fun print(indent: String, out: Appendable) {
            out.appendLine("$indent -> Target Class: $name")
        }
    }
    data class Subquery(val ast: QueryAST) : DataSource() {
        override fun print(indent: String, out: Appendable) {
            out.appendLine("$indent -> Target Subquery:")
            ast.printQuery(indent = "$indent    | ", out = out)
        }
    }

    abstract fun print(indent: String = "", out: Appendable = System.out)
}

data class QueryAST(
    val target: DataSource,
    val columns: List<Expression> = emptyList(),
    val columnNames: List<String> = emptyList(),
    val filter: Expression? = null,
    val orderBy: List<Pair<Expression, SortOrder>> = emptyList(), // <sort, sortDescending>
    val limit: Int? = null,
    val offset: Int? = null
) {
    fun printQuery(indent: String = "", out: Appendable = System.out) {
        target.print(indent = indent, out = out)

        out.append("$indent -> Columns:      ")
        if (columns.isEmpty())
            out.appendLine("${indent}ALL")
        else {
            out.appendLine()
            columns.forEach { printTree(it, indent = "$indent    ", out = out) }
        }
        out.appendLine("$indent -> Limit:        ${limit ?: "All"}")
        out.appendLine("$indent -> Order:        ")
        if (orderBy.isEmpty()) {
            out.appendLine("${indent}NONE")
        } else {
            out.appendLine()
            orderBy.forEach { (expr, order) ->
                out.appendLine("$indent      Direction: ${order.name}")
                printTree(expr, indent = "$indent      ", out = out)
            }
        }
        out.appendLine("$indent -> Logic Tree:")

        // Проверка
        if (filter != null) {
            printTree(filter, indent = "$indent    ", out = out)
        } else {
            out.appendLine("$indent    (No filter)")
        }
    }

    companion object {
        private fun createFromContext(selectCtx: ExprParser.SelectQueryContext): QueryAST {
            // 1. Имя класса либо подзапрос (используем метку target из грамматики)
            val target = selectCtx.target.run {
                className()?.let { DataSource.Class(it.text) } ?:
                DataSource.Subquery(createFromContext(selectQuery()))
            }

            // 2. Обработка колонок (раз уж ты добавил их в грамматику)
            val columnsList = mutableListOf<Expression>()
            val columnNames = mutableListOf<String>()
            val columnsCtx = selectCtx.columns()
            if (columnsCtx.STAR() == null) {
                // Если не звездочка, собираем список имен
                columnsCtx.columnList()?.column()?.forEach { column ->
                    val expr = column.expression()
                    columnsList.add(mapExpression(expr))
                    columnNames.add(column.name?.text ?: expr.text)
                }
            }
            // Если список пуст — значит выбраны все (*)

            // 3. Фильтрующее условие WHERE
            val whereClauses = selectCtx.additionalClause().mapNotNull { it.whereClause() }
            val filterExpr =
                if (whereClauses.isEmpty()) null
                else mapExpression(whereClauses.single().expression())

            // 4. Ограничивающий вывод LIMIT
            val limitClauses = selectCtx.additionalClause().mapNotNull { it.limitClause() }
            val limitValue =
                if (limitClauses.isEmpty()) null
                else limitClauses.single().count.text.toInt()

            // 5. Смещение вывода OFFSET
            val offsetClauses = selectCtx.additionalClause().mapNotNull { it.offsetClause() }
            val offsetValue =
                if (offsetClauses.isEmpty()) null
                else offsetClauses.single().count.text.toInt()

            // 6. Упорядочивание вывода ORDER BY
            val orderClauses = selectCtx.additionalClause().mapNotNull { it.orderClause() }
            val orderByList = mutableListOf<Pair<Expression, SortOrder>>()

            if (orderClauses.isNotEmpty()) {
                val elements = orderClauses.single().orderList().orderElement()

                elements.forEach { el ->
                    val expr = mapExpression(el.expression())
                    val order = if (el.DESC() != null) SortOrder.DESC else SortOrder.ASC
                    orderByList.add(expr to order)
                }
            }

            return QueryAST(
                target = target,
                filter = filterExpr,
                limit = limitValue,
                offset = offsetValue,
                orderBy = orderByList,
                columns = columnsList,
                columnNames = columnNames
            )
        }

        fun create(query: String): QueryAST {
            val charStream = CharStreams.fromString(query)
            val lexer = ExprLexer(charStream)
            val tokens = CommonTokenStream(lexer)
            val parser = ExprParser(tokens)

            // Важно: начинаем парсинг
            val tree = parser.root()
            val selectCtx = tree.selectQuery()

            return createFromContext(selectCtx)
        }

        // Рекурсивная функция для превращения дерева ANTLR в наш FilterExpr
        private fun mapExpression(ctx: ExprParser.ExpressionContext): Expression {
            return when (ctx) {
                // Случай: литералы
                is ExprParser.BoolLiteralExprContext -> {
                    Expression.Literal(BooleanCell(ctx.text.toBooleanStrict()))
                }
                is ExprParser.IntLiteralExprContext -> {
                    Expression.Literal(IntCell(ctx.text.toLong()))
                }
                is ExprParser.FloatLiteralExprContext -> {
                    Expression.Literal(FloatCell(ctx.text.toDouble()))
                }
                is ExprParser.StringLiteralExprContext -> {
                    Expression.Literal(StringCell(ctx.text.drop(1).dropLast(1)))
                }
                is ExprParser.NullLiteralExprContext -> {
                    Expression.Literal(NullCell)
                }

                // Случай: имя переменной
                is ExprParser.IdentifierExprContext -> {
                    Expression.Field(ctx.text)
                }

                // Соучай: left.right
                is ExprParser.AccessExprContext -> {
                    Expression.Access(
                        expr = mapExpression(ctx.left),
                        field = ctx.right.text
                    )
                }

                // Случай: name(arg1, arg2, ...)
                is ExprParser.FunctionCallExprContext -> {
                    val args = ctx.args?.expression()?.map { mapExpression(it) }.orEmpty()
                    Expression.FunctionCall(
                        name = ctx.name.text,
                        args = args
                    )
                }

                // Случай: left + right
                is ExprParser.PlusExprContext -> {
                    Expression.Plus(
                        left = mapExpression(ctx.left),
                        right = mapExpression(ctx.right)
                    )
                }

                // Случай: left - right
                is ExprParser.MinusExprContext -> {
                    Expression.Minus(
                        left = mapExpression(ctx.left),
                        right = mapExpression(ctx.right)
                    )
                }

                // Случай: left * right
                is ExprParser.MultExprContext -> {
                    Expression.Mult(
                        left = mapExpression(ctx.left),
                        right = mapExpression(ctx.right)
                    )
                }

                // Случай: left / right
                is ExprParser.DivExprContext -> {
                    Expression.Div(
                        left = mapExpression(ctx.left),
                        right = mapExpression(ctx.right)
                    )
                }

                // Случай: left AND right
                is ExprParser.AndExprContext -> {
                    Expression.And(
                        left = mapExpression(ctx.left),
                        right = mapExpression(ctx.right)
                    )
                }
                // Случай: left OR right
                is ExprParser.OrExprContext -> {
                    Expression.Or(
                        left = mapExpression(ctx.left),
                        right = mapExpression(ctx.right)
                    )
                }

                // Случай: булево сравнение
                is ExprParser.ConditionExprContext -> {
                    Expression.Comparison(
                        left = mapExpression(ctx.left),
                        right = mapExpression(ctx.right),
                        op = ctx.op.text
                    )
                }

                // Случай: ( expression ) - просто проваливаемся внутрь скобок
                is ExprParser.ParenExprContext -> {
                    mapExpression(ctx.expression())
                }

                else -> throw IllegalStateException("Unknown expression type: ${ctx.javaClass.simpleName}")
            }
        }
    }
}