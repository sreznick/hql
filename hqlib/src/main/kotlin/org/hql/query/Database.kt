package org.hql.query

import org.hql.hprof.heap.Heap
import org.hql.query.ast.QueryAST

class Database(val heap: Heap) {
    val tables = hashMapOf<String, HprofTable>()

    fun query(query: String) {
        val ast = QueryAST.create(query)
        val table = tables.getOrPut(ast.targetClassName) {
            HprofTable(heap, ast.targetClassName)
        }
        table.select(ast.columns, ast.columnsAsText, ast.filter, ast.limit ?: Int.MAX_VALUE)
    }
}