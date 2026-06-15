package org.hql.query.rows

import org.hql.ColumnNotFoundException
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
        return when {
            column == firstTableName -> RowCell(firstRow)
            column == secondTableName -> RowCell(secondRow)
            column.startsWith(firstTableName) -> firstRow[column.removePrefix("$firstTableName.")]
            column.startsWith(secondTableName) -> secondRow[column.removePrefix("$secondTableName.")]
            else -> {
                val firstCell = try {
                    firstRow[column]
                } catch (_: ColumnNotFoundException) {
                    null
                }
                val secondCell = try {
                    secondRow[column]
                } catch (_: ColumnNotFoundException) {
                    null
                }

                when {
                    firstCell == null && secondCell == null -> NullCell
                    firstCell != null -> firstCell
                    secondCell != null -> secondCell
                    else -> throw HQLQueryException("conflicting column name: $column")
                }
            }
        }
    }
}