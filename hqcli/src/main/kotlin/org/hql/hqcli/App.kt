package org.hql.hqcli

import org.hql.hprof.heap.Heap
import org.hql.query.Database
import org.hql.query.ast.QueryAST

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Error: please specify the path to hprof file as first argument")
        return
    }
    val path = args[0]

    // Если передали аргументы запускаем их, если нет — запускаем микротесты
    val queries = if (args.size > 1) {
        listOf(args.drop(1).joinToString(" ")).asSequence()
    } else {
        generateSequence {
            print(">> ")
            readLine()
        }
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
            ast.print()

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

