package org.hql.query.rows

import org.hql.HQLException
import org.hql.HQLQueryException
import org.hql.query.Cell
import org.hql.query.NullCell
import org.hql.query.Row
import org.hql.query.RowCell

/**
 * A [Row] that is produced as a result of JOIN operations.
 * Combines column values from the two child [Row]s, properly handling dot prefixes.
 */
class JoinRow(
    private val firstTableName: String,
    private val secondTableName: String,
    val firstRow: Row,
    val secondRow: Row,
): Row {
    override fun get(column: String): Cell {
        if (column == firstTableName)
            return RowCell(firstRow)
        if (column == secondTableName)
            return RowCell(secondRow)
        if (column.startsWith(firstTableName))
            return firstRow[column.removePrefix("$firstTableName.")]
        if (column.startsWith(secondTableName))
            return secondRow[column.removePrefix("$secondTableName.")]

        val firstCell = try {
            firstRow[column]
        } catch (_: HQLException) {
            null
        }
        val secondCell = try {
            secondRow[column]
        } catch (_: HQLException) {
            null
        }
        if (firstCell != null && secondCell != null) {
            throw HQLQueryException("conflicting column name: $column")
        }

        firstCell?.let { return it }
        secondCell?.let { return it }
        return NullCell
    }
}