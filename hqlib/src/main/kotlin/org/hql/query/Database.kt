package org.hql.query

import org.hql.hprof.heap.Heap
import org.hql.query.ast.Target
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

    fun targetToTable(target: Target): Table {
        return when (target) {
            is Target.Class -> getTable(target.name).withName(target.alias)
            is Target.Subquery -> query(target.ast, target.alias)
            is Target.Join -> {
                val left = targetToTable(target.left)
                val right = targetToTable(target.right)
                left.join(right, target.expr, target.type, target.alias)
            }
        }
    }

    private fun query(ast: QueryAST, alias: String? = null): Table {
        val table = targetToTable(ast.target)
        val result = table.select(
            columns = ast.columns,
            filter = ast.filter,
            orderBy = ast.orderBy,
            groupBy = ast.groupBy,
            having = ast.having,
            limit = ast.limit,
            offset = ast.offset,
            alias = alias
        )
        return result
    }

    fun query(query: String): Table = query(QueryAST.create(query))
}