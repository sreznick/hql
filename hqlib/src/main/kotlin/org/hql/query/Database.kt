package org.hql.query

import org.hql.hprof.heap.Heap
import org.hql.query.ast.QueryAST
import org.hql.query.tables.CoroutineTable
import org.hql.query.tables.HprofTable

class Database(val heap: Heap) {
   // val tables = hashMapOf<String, HprofTable>()
    val tables = hashMapOf<String, Table>()

    fun createTable(name: String): HprofTable {
        val cls = heap.getClassByName(name)
        return HprofTable(
            cls.instanceFieldTypes.keys.toList(),
            cls.instances.map { instance ->
                instance.fields.mapValues { Cell.fromInstance(it.value) }
            }
        )
    }

    fun query(query: String) {
        val ast = QueryAST.create(query)
//        val table = tables.getOrPut(ast.targetClassName) {
//            when (ast.targetClassName) {
//                "coroutines" -> CoroutineTable(heap)
//                else -> HprofTable(heap, ast.targetClassName)
//            }
//        }
        var table = tables.getOrPut(ast.targetClassName) {
            createTable(ast.targetClassName)
        }

        if (ast.columns.isNotEmpty()) {
            table = table.select(ast.columns, ast.columnNames)
        }
        ast.filter?.let { filter ->
            table = table.filter(filter)
        }
        ast.sort?.let { sort ->
            table = table.sort(sort, ast.sortDescending)
        }
        ast.limit?.let { limit ->
            table = table.limit(limit)
        }
        ast.offset?.let { offset ->
            table = table.offset(offset)
        }
        table.print()
    }
}