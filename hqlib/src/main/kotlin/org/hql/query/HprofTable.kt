package org.hql.query

import org.hql.query.expressions.Expression
import kotlin.math.max

class HprofTable(
    private val columns: List<String>,
    private val rows: List<Map<String, Cell>>
) {
    fun print() {
        val values = columns.map { column ->
            rows.map { instance ->
                instance[column].toString()
            }
        }

        val cols = columns.size
        if (cols == 0) return
        val rows = values[0].size
        if (rows == 0) return
        val lengths = (0..<cols).map { i ->
            max(values[i].maxOf { it.length }, columns[i].length)
        }
        var i = 0
        while (i < cols) {
            print(" ${columns[i].padEnd(lengths[i])} ")
            i++
        }
        println("\n")
        var j = 0
        while (j < rows) {
            i = 0
            while (i < cols) {
                print(" ${values[i][j].padEnd(lengths[i])} ")
                i++
            }
            println()
            j++
        }
    }

    fun select(columns: List<Expression>, columnNames: List<String>): HprofTable {
        val processedRows = rows.map { instance ->
            val newRow = mutableMapOf<String, Cell>()
            instance.forEach { (name, cell) ->
                newRow[name] = cell
            }
            columns.zip(columnNames).forEach { (expr, name) ->
                newRow[name] = expr.eval(instance)
            }
            newRow.toMap()
        }
        return HprofTable(columnNames, processedRows)
    }

    fun filter(filter: Expression): HprofTable {
        val processedInstances = rows.filter { instance ->
            val result = filter.eval(instance)
            if (result !is BooleanCell)
                throw RuntimeException("result of a filter expression should be boolean")
            result.v
        }
        return HprofTable(columns, processedInstances)
    }

    fun sort(sort: Expression, sortDescending: Boolean = false): HprofTable {
        val selector = { row: Map<String, Cell> -> sort.eval(row) }
        val processedInstances =
            if (sortDescending) rows.sortedByDescending(selector)
            else                rows.sortedBy(selector)
        return HprofTable(columns, processedInstances)
    }

    fun offset(offset: Int): HprofTable {
        return HprofTable(columns, rows.drop(offset))
    }

    fun limit(limit: Int): HprofTable {
        return HprofTable(columns, rows.take(limit))
    }
}