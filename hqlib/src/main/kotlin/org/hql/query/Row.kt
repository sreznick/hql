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

/** [Row] that resolves cells lazily through a [resolver] applied to the underlying row [source]. */
class ResolverRow<R>(
    private val source: R,
    private val knownColumns: List<String>,
    private val resolver: (R, String) -> Cell?
) : Row {
    override fun get(column: String): Cell =
        resolver(source, column) ?: throw ColumnNotFoundException(column, knownColumns)
}