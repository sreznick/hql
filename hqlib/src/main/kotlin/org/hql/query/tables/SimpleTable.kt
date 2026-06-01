package org.hql.query.tables

import org.hql.query.Row

/**
 * A simple table with explicitly passed columns and rows.
 * Useful for return values of operations on other tables.
 */
class SimpleTable<R : Row>(
    override val baseColumns: List<String>,
    override val rows: List<R>
) : Table()