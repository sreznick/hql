package org.hql.query.tables

import org.hql.query.BooleanCell
import org.hql.query.Cell
import org.hql.query.Row
import org.hql.query.ast.SortOrder
import org.hql.query.expressions.Expression
import org.hql.query.printer.TablePrinter

/**
 * Shared base for SQL-like tables. Subclasses provide the row source and per-row column resolution;
 * filtering, sorting, pagination, and rendering are handled here uniformly.
 */
abstract class Table {

    /** The full set of column names this table is known to expose by default */
    protected abstract val baseColumns: List<String>

    /** The base rows on which select operates */
    protected abstract val rows: List<Row>

    fun select(
        columns: List<Expression>,
        columnNames: List<String>,
        filter: Expression?,
        orderBy: List<Pair<Expression, SortOrder>>,
        limit: Int?,
        offset: Int?
    ): Table {
        val outputColumns = columnNames.ifEmpty { baseColumns }

        val richRows = rows.map { row ->
            if (columns.isEmpty()) row
            else {
                // SELECT expr1 [AS name1], ... — augment the row with computed columns
                val computed = HashMap<String, Cell>(columns.size)
                columns.zip(columnNames).forEach { (expr, name) ->
                    computed[name] = expr.eval(row)
                }
                ChainedRow(computed, row)
            }
        }

        var processed: List<Row> = richRows

        filter?.let { f ->
            processed = processed.filter { row ->
                val result = f.eval(row)
                if (result !is BooleanCell)
                    throw RuntimeException("result of a filter expression should be boolean")
                result.v
            }
        }

        if (orderBy.isNotEmpty()) {
            processed = processed.sortedWith { a, b ->
                for ((expr, order) in orderBy) {
                    val valA = expr.eval(a)
                    val valB = expr.eval(b)

                    val res = valA.compareTo(valB)
                    if (res != 0) {
                        return@sortedWith when (order) {
                            SortOrder.ASC -> res
                            SortOrder.DESC -> -res
                        }
                    }
                }
                0
            }
        }

        offset?.let { processed = processed.drop(it) }
        limit?.let { processed = processed.take(it) }

        return SimpleTable(outputColumns, processed)
    }

    // TODO: potentially pass different printers as a strategy
    fun print() {
        val cellRows = rows.map { row -> baseColumns.map { row[it] } }
        TablePrinter.print(baseColumns, cellRows)
    }
}

/** [Row] that consults [overlay] first and falls back to [base] — used to layer SELECT-computed columns over base columns. */
private class ChainedRow(private val overlay: Map<String, Cell>, private val base: Row) : Row {
    override fun get(column: String): Cell = overlay[column] ?: base[column]
}
