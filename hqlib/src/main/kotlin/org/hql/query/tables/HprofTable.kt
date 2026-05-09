package org.hql.query.tables

import org.hql.query.BooleanCell
import org.hql.query.Cell
import org.hql.query.Table
import org.hql.query.expressions.Expression
import kotlin.math.max

class HprofTable(
    private val baseColumns: List<String>,
    private val baseRows: List<Map<String, Cell>>
) : Table {

    override fun select(
        columns: List<Expression>,
        columnNames: List<String>,
        filter: Expression?,
        sort: Expression?,
        sortDescending: Boolean,
        limit: Int?,
        offset: Int?
    ) {
        val rowsWithComputed = if (columns.isEmpty()) baseRows else baseRows.map { row ->
            val newRow = row.toMutableMap()
            columns.zip(columnNames).forEach { (expr, name) ->
                newRow[name] = expr.eval(row)
            }
            newRow.toMap()
        }

        var processed = rowsWithComputed

        filter?.let { f ->
            processed = processed.filter { row ->
                val result = f.eval(row)
                if (result !is BooleanCell)
                    throw RuntimeException("result of a filter expression should be boolean")
                result.v
            }
        }

        sort?.let { s ->
            val selector = { row: Map<String, Cell> -> s.eval(row) }
            processed =
                if (sortDescending) processed.sortedByDescending(selector)
                else processed.sortedBy(selector)
        }

        offset?.let { processed = processed.drop(it) }
        limit?.let { processed = processed.take(it) }

        val outputColumns = if (columnNames.isEmpty()) baseColumns else columnNames
        printTable(outputColumns, processed)
    }

    private fun printTable(columnNames: List<String>, rows: List<Map<String, Cell>>) {
        val cols = columnNames.size
        if (cols == 0) return
        if (rows.isEmpty()) return

        val values = columnNames.map { column ->
            rows.map { row -> row[column].toString() }
        }
        val lengths = (0..<cols).map { i ->
            max(values[i].maxOf { it.length }, columnNames[i].length)
        }

        var i = 0
        while (i < cols) {
            print(" ${columnNames[i].padEnd(lengths[i])} ")
            i++
        }
        println("\n")
        var j = 0
        while (j < rows.size) {
            i = 0
            while (i < cols) {
                print(" ${values[i][j].padEnd(lengths[i])} ")
                i++
            }
            println()
            j++
        }
    }
}
