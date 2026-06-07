package org.hql.query.tables

import org.hql.HQLQueryException
import org.hql.query.BooleanCell
import org.hql.query.Cell
import org.hql.query.Row
import org.hql.query.ast.JoinType
import org.hql.query.ast.NamedExpression
import org.hql.query.ast.NamedExpression.Companion.asMap
import org.hql.query.ast.SortOrder
import org.hql.query.expressions.Expression
import org.hql.query.printer.TablePrinter
import org.hql.query.rows.AggregateRow
import org.hql.query.rows.EmptyRow
import org.hql.query.rows.JoinRow

/**
 * Shared base for SQL-like tables. Subclasses provide the row source and per-row column resolution;
 * filtering, sorting, pagination, and rendering are handled here uniformly.
 */
abstract class Table {
    /** The name of the table */
    lateinit var name: String

    /** The full set of column names this table is known to expose by default */
    protected abstract val baseColumns: List<String>

    /** The base rows on which select operates */
    protected abstract val rows: List<Row>

    fun select(
        columns: List<NamedExpression>,
        filter: Expression?,
        orderBy: List<Pair<Expression, SortOrder>>,
        groupBy: List<NamedExpression>,
        having: Expression?,
        limit: Int?,
        offset: Int?
    ): Table {
        val outputColumns = columns.map { it.name }.ifEmpty { baseColumns }
        val columnsAsMap = columns.asMap()

        val richRows = rows.map { row ->
            if (columns.isEmpty()) row
            else {
                // SELECT expr1 [AS name1], ... — augment the row with computed columns
                ChainedRow(row) { column ->
                    columnsAsMap[column]?.eval(row)
                }
            }
        }

        var processed: List<Row> = richRows

        filter?.let { f ->
            processed = processed.filter { row ->
                val result = f.eval(row)
                if (result !is BooleanCell)
                    throw HQLQueryException("result of a filter expression should be boolean")
                result.v
            }
        }

        if (groupBy.isNotEmpty()) {
            val groupedCells = mutableMapOf<List<Cell>, MutableList<Row>>()
            val groupNames = groupBy.map { it.name }
            processed.forEach { row ->
                val values = groupBy.map { (expr, _) -> expr.eval(row) }
                groupedCells.getOrPut(values) { mutableListOf() }.add(row)
            }
            processed = groupedCells.map { (key, rows) ->
                val newRow = AggregateRow(
                    groupNames.zip(key).associate { it },
                    rows,
                )

                // a second ChainedRow is required here so that expressions with
                // aggregate functions don't compute against the original table rows
                ChainedRow(newRow) { column ->
                    columnsAsMap[column]?.eval(newRow)
                }
            }

            having?.let { f ->
                processed = processed.filter { row ->
                    val result = f.eval(row)
                    if (result !is BooleanCell)
                        throw HQLQueryException("result of a filter expression should be boolean")
                    result.v
                }
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

        return SimpleTable(name, outputColumns, processed)
    }

    fun join(other: Table, expr: Expression, type: JoinType, alias: String): Table {
        val newColumns = baseColumns.map { "$name.$it" } + other.baseColumns.map { "${other.name}.$it" }
        val rowsPresent = MutableList(rows.size) { false }
        val othersPresent = MutableList(other.rows.size) { false }
        val newRows = rows.flatMapIndexed { rowIndex, row ->
            other.rows.mapIndexedNotNull { otherRowIndex, otherRow ->
                val joinRow = JoinRow(name, other.name, row, otherRow)
                val condResult = expr.eval(joinRow)
                if (condResult !is BooleanCell)
                    throw HQLQueryException("result of a join expression should be boolean")
                if (condResult.v) {
                    rowsPresent[rowIndex] = true
                    othersPresent[otherRowIndex] = true
                    joinRow
                } else null
            }
        }.toMutableList()

        if (type == JoinType.LEFT || type == JoinType.FULL) {
            rowsPresent.forEachIndexed { i, present ->
                if (!present)
                    newRows.add(JoinRow(name, other.name, rows[i], EmptyRow(other.baseColumns)))
            }
        }
        if (type == JoinType.RIGHT || type == JoinType.FULL) {
            othersPresent.forEachIndexed { i, present ->
                if (!present)
                    newRows.add(JoinRow(name, other.name, EmptyRow(baseColumns), other.rows[i]))
            }
        }

        return SimpleTable(alias, newColumns, newRows)
    }

    // TODO: potentially pass different printers as a strategy
    fun print(out: Appendable = System.out) {
        val cellRows = rows.map { row -> baseColumns.map { row[it] } }
        TablePrinter.print(baseColumns, cellRows, out)
    }
}

/** [Row] that consults [overlay] first and falls back to [base] — used to layer SELECT-computed columns over base columns. */
private class ChainedRow(private val base: Row, private val overlay: (String) -> Cell?) : Row {
    override fun get(column: String): Cell = overlay(column) ?: base[column]
}
