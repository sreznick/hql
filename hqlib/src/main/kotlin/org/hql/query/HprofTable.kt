package org.hql.query

import org.hql.hprof.heap.Class
import org.hql.hprof.heap.Heap
import org.hql.hprof.heap.Instance
import org.hql.query.expressions.Expression
import kotlin.math.max

class HprofTable(private val heap: Heap, className: String) {
    private val cls: Class
    private val fieldNames: Set<String>
    private val instances: List<Instance>

    init {
        try {
            cls = heap.getClassByName(className)
        } catch (_: NullPointerException) {
            throw RuntimeException("no such class: $className")
        }
        fieldNames = cls.getInstanceFieldTypes().map { it.key }.toSet()
        instances = cls.getInstances()
    }

    private fun printTable(columns: List<String>, values: List<List<String>>) {
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

    fun select(
        columns: List<Expression>,
        columnNames: List<String>,
        filter: Expression? = null,
        sort: Expression? = null,
        sortDescending: Boolean = false,
        limit: Int? = null,
        offset: Int? = null
    ) {
        val columns_ = columns.ifEmpty { fieldNames.map { Expression.Field(it) } }
        val columnsAsText_ = columnNames.ifEmpty { fieldNames.toList() }

        var processedInstances = instances
        filter?.let {
            processedInstances = processedInstances.filter { instance ->
                val result = filter.eval(instance)
                if (result !is Boolean)
                    throw RuntimeException("result of a filter expression should be boolean")
                result
            }
        }
        sort?.let {
            val selector: (Instance) -> Comparable<Any?> = { instance ->
                val result = sort.eval(instance)
                val comparable = result as? Comparable<Any?>
                    ?: throw RuntimeException("result (type ${if (result == null) null else result::class.simpleName}) is not comparable")
                comparable
            }
            processedInstances =
                if (sortDescending) processedInstances.sortedByDescending(selector)
                else                processedInstances.sortedBy(selector)
        }
        offset?.let {
            processedInstances = processedInstances.drop(offset)
        }
        limit?.let {
            processedInstances = processedInstances.take(limit)
        }

        val values = columns_.map { column ->
            processedInstances.map { instance ->
                column.eval(instance).toString()
            }
        }
        printTable(columnsAsText_, values)
    }
}