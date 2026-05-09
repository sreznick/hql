package org.hql.query

import org.hql.query.expressions.Expression

/**
 * Provides SQL-like operations (filtering, sorting, pagination) over a heap dump.
 * The table operates on already prepared models and does not access the heap directly
 */
interface Table {

    fun select(
        columns: List<Expression>,
        columnNames: List<String>,
        filter: Expression? = null,
        sort: Expression? = null,
        sortDescending: Boolean = false,
        limit: Int? = null,
        offset: Int? = null
    )
}