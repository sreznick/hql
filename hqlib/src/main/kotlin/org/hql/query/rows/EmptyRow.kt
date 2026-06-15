package org.hql.query.rows

import org.hql.ColumnNotFoundException
import org.hql.query.Cell
import org.hql.query.NullCell
import org.hql.query.Row

/**
 * A [Row] returning null values for the specified columns.
 * Used as a placeholder row for outer JOIN operations.
 */
class EmptyRow(private val columns: List<String>): Row {
    override fun get(column: String): Cell {
        return when (column) {
            in columns -> NullCell
            else -> throw ColumnNotFoundException(column, columns)
        }
    }
}