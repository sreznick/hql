package org.hql.query.tables

import org.hql.ColumnNotFoundException
import org.hql.query.Cell

class HprofTable(
    override val baseColumns: List<String>,
    override val rows: List<Map<String, Cell>>
) : AbstractTable<Map<String, Cell>>() {

    override fun resolveCell(row: Map<String, Cell>, column: String): Cell =
        row[column] ?: throw ColumnNotFoundException(column, row.keys.toList())
}
