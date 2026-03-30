package org.hql.query

import org.hql.hprof.heap.Instance
import org.hql.query.expressions.Expression
import kotlin.math.max

class HprofTable(
    private val columns: List<String>,
    private val instances: List<Instance.ObjectI>
) {
    fun print() {
        val values = columns.map { column ->
            instances.map { instance ->
                instance[column].toString()
            }
        }

        val cols = columns.size
        val rows = values[0].size
        if (cols == 0 || rows == 0) return
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
        val processedInstances = instances.map { instance ->
            val newInstance = Instance.ObjectI()
            instance.getFields().forEach { (name, instance) ->
                newInstance.addField(name, instance)
            }
            columns.zip(columnNames).forEach { (expr, name) ->
                newInstance.addField(name, expr.eval(instance))
            }
            newInstance
        }
        return HprofTable(columnNames, processedInstances)
    }

    fun filter(filter: Expression): HprofTable {
        val processedInstances = instances.filter { instance ->
            val result = filter.eval(instance)
            if (result !is Instance.BooleanI)
                throw RuntimeException("result of a filter expression should be boolean")
            result.v
        }
        return HprofTable(columns, processedInstances)
    }

    fun sort(sort: Expression, sortDescending: Boolean = false): HprofTable {
        val selector = { instance: Instance -> sort.eval(instance) }
        val processedInstances =
            if (sortDescending) instances.sortedByDescending(selector)
            else                instances.sortedBy(selector)
        return HprofTable(columns, processedInstances)
    }

    fun offset(offset: Int): HprofTable {
        return HprofTable(columns, instances.drop(offset))
    }

    fun limit(limit: Int): HprofTable {
        return HprofTable(columns, instances.take(limit))
    }
}