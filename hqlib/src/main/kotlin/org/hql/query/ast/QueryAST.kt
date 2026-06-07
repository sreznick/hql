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
        is Expression.Not -> {
            out.appendLine("$indent[NOT]")
            printTree(expr.expr, "$indent  |", out = out)
        }
    }
}

enum class JoinType {
    INNER, LEFT, RIGHT, FULL
}

sealed class Target {
    data class Class(val name: String, val alias: String) : Target() {
        override fun print(indent: String, out: Appendable) {
            out.appendLine("$indent    Class $name")
        }
    }
    data class Subquery(val ast: QueryAST, val alias: String) : Target() {
        override fun print(indent: String, out: Appendable) {
            out.appendLine("$indent    Subquery")
            ast.printQuery(indent = "$indent    | ", out = out)
        }
    }

    data class Join(
        val left: Target,
        val right: Target,
        val expr: Expression,
        val type: JoinType,
        val alias: String
    ) : Target() {
        override fun print(indent: String, out: Appendable) {
            out.appendLine("$indent    Join")
            out.appendLine("$indent    | Type: ${type.name}")
            out.appendLine("$indent    | Join condition:")
            printTree(expr, indent = "$indent    |   ", out = out)
            out.appendLine("$indent    | Left:")
            left.print(indent = "$indent    |   ", out = out)
            out.appendLine("$indent    | Right:")
            right.print(indent = "$indent    |   ", out = out)
        }
    }

    abstract fun print(indent: String = "", out: Appendable = System.out)
}

data class NamedExpression(
    val expr: Expression,
    val name: String
) {
    companion object {
        fun List<NamedExpression>.asMap(): Map<String, Expression> =
            associate { it.name to it.expr }
    }
}

data class QueryAST(
    val target: Target,
    val columns: List<NamedExpression> = emptyList(),
    val filter: Expression? = null,
    val orderBy: List<Pair<Expression, SortOrder>> = emptyList(), // <sort, sortDescending>
    val groupBy: List<NamedExpression> = emptyList(),
    val having: Expression? = null,
    val limit: Int? = null,
    val offset: Int? = null
) {
    fun printQuery(indent: String = "", out: Appendable = System.out) {
        out.appendLine("$indent -> Target:")
        target.print(indent = indent, out = out)

        out.append("$indent -> Columns:      ")
        if (columns.isEmpty())
            out.appendLine("${indent}ALL")
        else {
            out.appendLine()
            columns.forEach { printTree(it.expr, indent = "$indent    ", out = out) }
        }
        out.appendLine("$indent -> Limit:        ${limit ?: "All"}")
        out.appendLine("$indent -> Order:        ")
        if (orderBy.isEmpty()) {
            out.appendLine("$indent      NONE")
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

        out.append("$indent -> Group:      ")
        if (groupBy.isEmpty())
            out.appendLine("$indent      NONE")
        else {
            out.appendLine()
            groupBy.forEach { printTree(it.expr, indent = "$indent    ", out = out) }
        }
    }

    companion object {
        private fun targetFromContext(ctx: ExprParser.TableContext): Target {
            val alias = ctx.name?.text ?: ctx.className()?.text ?: $$"$selectResult"
            val a = ctx.className()?.let { Target.Class(it.text, alias) } ?:
                Target.Subquery(createFromContext(ctx.selectQuery()), alias)

            return ctx.joinClause()?.let { joinCtx ->
                val joinType = joinCtx.LEFT()?.let {
                    JoinType.LEFT
                } ?: joinCtx.RIGHT()?.let {
                    JoinType.RIGHT
                } ?: joinCtx.FULL()?.let {
                    JoinType.FULL
                } ?: JoinType.INNER
                Target.Join(
                    left = a,
                    right = targetFromContext(joinCtx.right),
                    type = joinType,
                    expr = mapExpression(joinCtx.expr),
                    alias = $$"$joinResult"
                )
            } ?: a
        }

        private fun createFromContext(selectCtx: ExprParser.SelectQueryContext): QueryAST {
            // 1. Имя класса либо подзапрос (используем метку target из грамматики)
            val target = targetFromContext(selectCtx.target)

            // 2. Обработка колонок (раз уж ты добавил их в грамматику)
            val columns = mutableListOf<NamedExpression>()
            val columnsCtx = selectCtx.columns()
            if (columnsCtx.STAR() == null) {
                // Если не звездочка, собираем список имен
                columnsCtx.columnList()?.column()?.forEach { column ->
                    val expr = column.expression()
                    columns.add(NamedExpression(
                        mapExpression(expr),
                        column.name?.text ?: expr.text
                    ))
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

            // 7. Группировка вывода GROUP BY
            val groupClauses = selectCtx.additionalClause().mapNotNull { it.groupClause() }
            val groupBy = mutableListOf<NamedExpression>()
            if (groupClauses.isNotEmpty()) {
                val elements = groupClauses.single().columnList().column()
                elements.forEach { column ->
                    val expr = column.expression()
                    groupBy.add(NamedExpression(
                        mapExpression(expr),
                        column.name?.text ?: expr.text
                    ))
                }
            }

            // 8. Фильтрация сгруппированных строк HAVING
            val havingClauses = selectCtx.additionalClause().mapNotNull { it.havingClause() }
            val havingExpr =
                if (havingClauses.isEmpty()) null
                else mapExpression(havingClauses.single().expression())

            return QueryAST(
                target = target,
                columns = columns,
                filter = filterExpr,
                limit = limitValue,
                offset = offsetValue,
                orderBy = orderByList,
                groupBy = groupBy,
                having = havingExpr,
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
                // Случай: NOT expr
                is ExprParser.NotExprContext -> {
                    Expression.Not(
                        expr = mapExpression(ctx.expr)
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