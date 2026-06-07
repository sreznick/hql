package org.hql.query.rows

import org.hql.query.AggregateCell
import org.hql.query.Cell
import org.hql.query.Row


/**
 * A [Row] that itself contains a group of other [Row]s.
 * Used as a result of GROUP BY operations, and is passed as
 * an input to aggregate functions like count().
 */
class AggregateRow(
    val values: Map<String, Cell>,
    val group: List<Row>
): Row {
    override fun get(column: String): Cell {
        return values[column] ?:
            AggregateCell(group.map { row -> row[column] })
    }
}