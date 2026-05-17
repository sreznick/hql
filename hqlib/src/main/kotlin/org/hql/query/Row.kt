package org.hql.query

import org.hql.ColumnNotFoundException

/**
 * Read-only column lookup for a single table row.
 * Implementations decide whether values are precomputed or resolved on demand.
 */
interface Row {
    /** Returns the cell for [column] or throws [ColumnNotFoundException] if it is unknown to this row. */
    operator fun get(column: String): Cell
}

/** [Row] backed by a precomputed map of cells (used for tables whose columns are materialized up-front). */
class MapRow(private val cells: Map<String, Cell>) : Row {
    override fun get(column: String): Cell =
        cells[column] ?: throw ColumnNotFoundException(column, cells.keys.toList())
}