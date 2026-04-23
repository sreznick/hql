package org.hql.hqcli

import org.hql.HQLException
import org.hql.hprof.heap.Heap
import org.hql.hprof.reader.HprofReader
import org.hql.query.Database
import org.hql.query.ast.QueryAST
import java.io.File

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
    val reader = File(path).inputStream().use { stream ->
        HprofReader(stream)
    }
    val heap = Heap(reader.getHprof())
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
            db.query(query)
        } catch (e: HQLException) {
            println("Error: ${e.message}")
        } catch (e: Exception) {
            println("Uncaught error parsing query: ${e.message}")
            e.printStackTrace()
        }
        println("-".repeat(40) + "\n")
    }
}

