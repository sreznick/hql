package org.hql.query

import org.hql.hprof.heap.Heap
import org.hql.query.ast.QueryAST
import org.hql.query.tables.CoroutineTable
import org.hql.query.tables.ClassTable
import org.hql.query.tables.Table

class Database(val heap: Heap) {
    val tables = hashMapOf<String, Table>()

    fun getTable(name: String): Table = tables.getOrPut(name) {
        when (name) {
            "coroutines" -> CoroutineTable(heap)
            else -> ClassTable(heap.getClassByName(name))
        }
    }

    private fun query(ast: QueryAST): Table {
        val table = if (ast.subquery != null) query(ast.subquery) else getTable(ast.targetClassName)
        return table.select(
            columns = ast.columns,
            columnNames = ast.columnNames,
            filter = ast.filter,
            orderBy = ast.orderBy,
            limit = ast.limit,
            offset = ast.offset
        )
    }

    fun query(query: String): Table = query(QueryAST.create(query))
}