package org.hql.query

import org.hql.hprof.heap.Heap
import org.hql.query.ast.FilterExpr

class Database(val heap: Heap) {
    val tables = hashMapOf<String, HprofTable>()

    fun query(
        targetClassName: String,
        columns: List<String>,
        limit: Int?,
        filter: FilterExpr?
    ) {
        val table = tables.getOrPut(targetClassName) {
            HprofTable(heap, targetClassName)
        }
        table.select(columns, filter, limit ?: Int.MAX_VALUE)
    }
}