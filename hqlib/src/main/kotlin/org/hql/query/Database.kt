package org.hql.query

import org.hql.hprof.heap.Heap
import org.hql.query.ast.QueryAST

class Database(val heap: Heap) {
    val tables = hashMapOf<String, HprofTable>()

    fun createTable(name: String): HprofTable {
        val cls = heap.getClassByName(name)
        return HprofTable(
            cls.getInstanceFieldTypes().map { it.key }.toList(),
            cls.getInstances().map { instance ->
                instance.getFields().mapValues { Cell.fromInstance(it.value) }
            }
        )
    }

    fun query(query: String) {
        val ast = QueryAST.create(query)
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