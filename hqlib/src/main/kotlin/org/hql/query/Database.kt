package org.hql.query

import org.hql.hprof.heap.Heap
import org.hql.query.ast.QueryAST
import org.hql.query.tables.CoroutineTable
import org.hql.query.tables.HprofTable

class Database(val heap: Heap) {
    val tables = hashMapOf<String, Table>()

    private fun createHprofTable(name: String): HprofTable {
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
        val table = tables.getOrPut(ast.targetClassName) {
            when (ast.targetClassName) {
                "coroutines" -> CoroutineTable(heap)
                else -> createHprofTable(ast.targetClassName)
            }
        }
        table.select(
            columns = ast.columns,
            columnNames = ast.columnNames,
            filter = ast.filter,
            sort = ast.sort,
            sortDescending = ast.sortDescending,
            limit = ast.limit,
            offset = ast.offset
        )
    }
}