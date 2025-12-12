package org.hql.query.ast

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.hql.ExprLexer
import org.hql.ExprParser

// Структура для фильтров
sealed class FilterExpr {
    // Лист дерева: конкретное сравнение
    data class Comparison(val field: String, val operator: String, val value: Any) : FilterExpr()

    // Узлы дерева: логика
    data class And(val left: FilterExpr, val right: FilterExpr) : FilterExpr()
    data class Or(val left: FilterExpr, val right: FilterExpr) : FilterExpr()
}

data class QueryAST(
    val targetClassName: String,
    val filter: FilterExpr? = null,
    val limit: Int? = null,
    val columns: List<String> = emptyList()
) {
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
            val columnsList = mutableListOf<String>()
            val columnsCtx = selectCtx.columns()
            if (columnsCtx.STAR() == null) {
                // Если не звездочка, собираем список имен
                val colListCtx = columnsCtx.columnList()
                if (colListCtx != null) {
                    colListCtx.IDENTIFIER().forEach { columnsList.add(it.text) }
                }
            }
            // Если список пуст — значит выбраны все (*)

            // 3. Фильтрующее условие WHERE
            val whereCtx = selectCtx.whereClause()

            val filterObj: FilterExpr? = if (whereCtx != null) {
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
                columns = columnsList
            )
        }

        // Рекурсивная функция для превращения дерева ANTLR в наш FilterExpr
        private fun mapExpression(ctx: ExprParser.ExpressionContext): FilterExpr {
            return when (ctx) {
                // Случай: left AND right
                is ExprParser.AndExprContext -> {
                    FilterExpr.And(
                        left = mapExpression(ctx.left),
                        right = mapExpression(ctx.right)
                    )
                }
                // Случай: left OR right
                is ExprParser.OrExprContext -> {
                    FilterExpr.Or(
                        left = mapExpression(ctx.left),
                        right = mapExpression(ctx.right)
                    )
                }
                // Случай: ( expression ) - просто проваливаемся внутрь скобок
                is ExprParser.ParenExprContext -> {
                    mapExpression(ctx.expression())
                }
                // Случай: condition (базовое сравнение)
                is ExprParser.AtomExprContext -> {
                    val condCtx = ctx.condition()
                    val fieldName = condCtx.field.text
                    val operator = condCtx.op.text

                    val valueCtx = condCtx.literal()
                    val rawValue = valueCtx.text

                    val parsedValue: Any = when {
                        valueCtx.INT_LITERAL() != null -> rawValue.toInt()
                        else -> rawValue.trim('\'') // Удаляем кавычки у строк
                    }

                    FilterExpr.Comparison(fieldName, operator, parsedValue)
                }
                else -> throw IllegalStateException("Unknown expression type: ${ctx.javaClass.simpleName}")
            }
        }
    }
}