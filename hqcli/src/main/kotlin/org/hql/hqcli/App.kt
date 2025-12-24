package org.hql.hqcli

import org.hql.hprof.heap.Heap
import org.hql.query.Database
import org.hql.query.ast.FilterExpr
import org.hql.query.ast.QueryAST

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Error: please specify the path to hprof file as first argument")
        return
    }
    val path = args[0]

    // Если передали аргументы запускаем их, если нет — запускаем микротесты
    val queries = if (args.size > 1) {
        listOf(args.drop(1).joinToString(" "))
    } else {
        listOf(
            // Тест 0: Вывести таблицу
            "SELECT * FROM User",

            // Тест 1: Обычный запрос
            "SELECT name, age FROM User WHERE age > 18 AND active = 'true' LIMIT 10",

            // Тест 2: Проверка приоритетов (AND сильнее OR)
            // Ожидаем: age > 18 ИЛИ (city = 'Msk' И active = 'true')
            "SELECT * FROM User WHERE age > 18 OR city = 'Msk' AND active = 'true'",

            // Тест 3: Скобки меняют приоритет
            // Ожидаем: (age > 18 ИЛИ city = 'Msk') И active = 'true'
            "SELECT * FROM User WHERE (age > 18 OR city = 'Msk') AND active = 'true'"
        )
    }

    println("Running HQL CLI...\n")
    val heap = Heap(path)
    val db = Database(heap)

    for ((i, query) in queries.withIndex()) {
        println("=== QUERY #${i + 1} ===")
        println("SQL: \"$query\"")

        try {
            val ast = QueryAST.create(query)

            println("Parsed successfully:")
            println(" -> Target Class: ${ast.targetClassName}")
            println(" -> Columns:      ${if (ast.columns.isEmpty()) "*" else ast.columns.joinToString(", ")}")
            println(" -> Limit:        ${ast.limit ?: "All"}")
            println(" -> Logic Tree:")

            // Проверка
            val currentFilter = ast.filter
            if (currentFilter != null) {
                printTree(currentFilter, indent = "    ")
            } else {
                println("    (No filter)")
            }

            println("-".repeat(40) + "\n")
            println("Query results:")
            db.query(ast.targetClassName, ast.columns,
                filter = ast.filter,
                limit = ast.limit
            )
        } catch (e: Exception) {
            println("ERROR parsing query: ${e.message}")
            e.printStackTrace()
        }
        println("-".repeat(40) + "\n")
    }
}

// Рекурсивная функция для рисования дерева логики
fun printTree(expr: FilterExpr, indent: String) {
    when (expr) {
        is FilterExpr.And -> {
            println("$indent[AND]")
            printTree(expr.left, "$indent  |")
            printTree(expr.right, "$indent  |")
        }
        is FilterExpr.Or -> {
            println("$indent[OR]")
            printTree(expr.left, "$indent  |")
            printTree(expr.right, "$indent  |")
        }
        is FilterExpr.Comparison -> {
            println("$indent-> ${expr.field} ${expr.operator} ${expr.value}")
        }
    }
}