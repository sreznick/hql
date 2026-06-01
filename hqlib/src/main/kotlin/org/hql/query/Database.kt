package org.hql.query

import org.hql.hprof.heap.Heap
import org.hql.query.ast.DataSource
import org.hql.query.ast.QueryAST
import org.hql.query.tables.CoroutineTable
import org.hql.query.tables.ThreadTable
import org.hql.query.tables.ClassTable
import org.hql.query.tables.Table

class Database(val heap: Heap) {
    val tables = hashMapOf<String, Table>()

    fun getTable(name: String): Table = tables.getOrPut(name) {
        when (name) {
            "coroutines" -> CoroutineTable(heap)
            "threads" -> ThreadTable(heap)
            else -> ClassTable(heap.getClassByName(name))
        }
    }

    private fun query(ast: QueryAST): Table {
        val table = when (ast.target) {
            is DataSource.Class -> getTable(ast.target.name)
            is DataSource.Subquery -> query(ast.target.ast)
        }
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