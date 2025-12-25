package org.hql.query.ast

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.hql.ExprLexer
import org.hql.ExprParser
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
    }
}

data class QueryAST(
    val targetClassName: String,
    val filter: Expression? = null,
    val limit: Int? = null,
    val columns: List<Expression> = emptyList(),
    val columnsAsText: List<String> = emptyList()
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
            val columnsAsText = mutableListOf<String>()
            val columnsCtx = selectCtx.columns()
            if (columnsCtx.STAR() == null) {
                // Если не звездочка, собираем список имен
                columnsCtx.columnList()?.expression()?.forEach { expr ->
                    columnsList.add(mapExpression(expr))
                    columnsAsText.add(expr.text)
                }
            }
            // Если список пуст — значит выбраны все (*)

            // 3. Фильтрующее условие WHERE
            val whereCtx = selectCtx.whereClause()

            val filterObj: Expression? = if (whereCtx != null) {
                val exprCtx = whereCtx.expression()
                // Вызываем нашу рекурсивную функцию
                mapExpression(exprCtx)
            } else {
                null
            }

            // 4. Ограничивающий вывод LIMIT
            val limitCtx = selectCtx.limitClause()
            val limitValue = limitCtx?.count?.text?.toInt()

            return QueryAST(
                targetClassName = className,
                filter = filterObj,
                limit = limitValue,
                columns = columnsList,
                columnsAsText = columnsAsText
            )
        }

        // Рекурсивная функция для превращения дерева ANTLR в наш FilterExpr
        private fun mapExpression(ctx: ExprParser.ExpressionContext): Expression {
            return when (ctx) {
                // Случай: литералы
                is ExprParser.BoolLiteralExprContext -> {
                    Expression.Literal(ctx.text.toBooleanStrict())
                }
                is ExprParser.IntLiteralExprContext -> {
                    Expression.Literal(ctx.text.toLong())
                }
                is ExprParser.FloatLiteralExprContext -> {
                    Expression.Literal(ctx.text.toDouble())
                }
                is ExprParser.StringLiteralExprContext -> {
                    Expression.Literal(ctx.text.drop(1).dropLast(1))
                }
                is ExprParser.NullLiteralExprContext -> {
                    Expression.Literal(null)
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