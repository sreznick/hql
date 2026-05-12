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
private fun printTree(expr: Expression, indent: String) {
    when (expr) {
        is Expression.Field ->
            println("$indent[FIELD=${expr.field}]")
        is Expression.Literal ->
            println("$indent[LITERAL=${expr.value}]")

        is Expression.And -> {
            println("$indent[AND]")
            printTree(expr.left, "$indent  |")
            printTree(expr.right, "$indent  |")
        }
        is Expression.Or -> {
            println("$indent[OR]")
            printTree(expr.left, "$indent  |")
            printTree(expr.right, "$indent  |")
        }
        is Expression.Comparison -> {
            println("$indent[COMPARISON ${expr.op}]")
            printTree(expr.left, "$indent  |")
            printTree(expr.right, "$indent  |")
        }

        is Expression.Access -> {
            println("$indent[ACCESS ${expr.field}]")
            printTree(expr.expr, "$indent  |")
        }
        is Expression.Div -> {
            println("$indent[DIV]")
            printTree(expr.left, "$indent  |")
            printTree(expr.right, "$indent  |")
        }
        is Expression.Minus -> {
            println("$indent[MINUS]")
            printTree(expr.left, "$indent  |")
            printTree(expr.right, "$indent  |")
        }
        is Expression.Mult -> {
            println("$indent[MULT]")
            printTree(expr.left, "$indent  |")
            printTree(expr.right, "$indent  |")
        }
        is Expression.Plus -> {
            println("$indent[PLUS]")
            printTree(expr.left, "$indent  |")
            printTree(expr.right, "$indent  |")
        }
        is Expression.FunctionCall -> {
            println("$indent[FUNCTION_CALL=${expr.name}]")
            expr.args.forEach { printTree(it, "$indent  |") }
        }
    }
}

data class QueryAST(
    val targetClassName: String,
    val columns: List<Expression> = emptyList(),
    val columnNames: List<String> = emptyList(),
    val filter: Expression? = null,
    val orderBy: List<Pair<Expression, SortOrder>> = emptyList(), // <sort, sortDescending>
    val limit: Int? = null,
    val offset: Int? = null
) {
    fun print() {
        println(" -> Target Class: $targetClassName")
        print(" -> Columns:      ")
        if (columns.isEmpty())
            println("ALL")
        else {
            println()
            columns.forEach { printTree(it, indent = "    ") }
        }
        println(" -> Limit:        ${limit ?: "All"}")
        println(" -> Order:        ")
        if (orderBy.isEmpty()) {
            println("NONE")
        } else {
            println()
            orderBy.forEach { (expr, order) ->
                println("      Direction: ${order.name}")
                printTree(expr, indent = "      ")
            }
        }
        println(" -> Logic Tree:")

        // Проверка
        if (filter != null) {
            printTree(filter, indent = "    ")
        } else {
            println("    (No filter)")
        }
    }

    companion object {
        fun create(query: String): QueryAST {
            val charStream = CharStreams.fromString(query)
            val lexer = ExprLexer(charStream)
            val tokens = CommonTokenStream(lexer)
            val parser = ExprParser(tokens)

            // Важно: начинаем парсинг
            val tree = parser.root()
            val selectCtx = tree.selectQuery()

            // 1. Имя класса (используем метку target из грамматики)
            val className = selectCtx.target.text

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
                targetClassName = className,
                filter = filterExpr,
                limit = limitValue,
                offset = offsetValue,
                orderBy = orderByList,
                columns = columnsList,
                columnNames = columnNames
            )
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