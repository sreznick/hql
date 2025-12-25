package org.hql.query

import org.hql.hprof.heap.Class
import org.hql.hprof.heap.Heap
import org.hql.hprof.heap.Instance
import org.hql.query.expressions.Expression
import kotlin.collections.toByteArray
import kotlin.math.max

private fun repr(value: Any?): String {
    if (value == null) return "null"
    if (value is List<*> && value.isNotEmpty() && value[0] is Byte) {
        val content = (value as List<Byte>).toByteArray().toString(Charsets.UTF_8)
        return "\"$content\""
    }
    if (value is Instance && value.getType() == "java.lang.String") {
        return repr(value["value"])
    }
    return value.toString()
}

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
        if (cols == 0) return
        val rows = values[0].size
        val lengths = (0..<cols).map { i ->
            max(values[i].maxOf { it.length}, columns[i].length)
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

    fun select(columns: List<Expression>, columnsAsText: List<String>, filter: Expression?, limit: Int) {
        val columns_ = columns.ifEmpty { fieldNames.map { Expression.Field(it) } }
        val columnsAsText_ = columnsAsText.ifEmpty { fieldNames.toList() }

        val filterF: (Instance) -> Boolean = if (filter != null)
            { instance ->
                val result = filter.eval(instance)
                if (result !is Boolean)
                    throw RuntimeException("result of a filter expression should be boolean")
                result
            } else { _ -> true }

        val filteredInstances = instances
            .filter(filterF)
            .take(limit)
        val values = columns_.map { column ->
            filteredInstances.map { instance ->
                repr(column.eval(instance))
            }
        }
        printTable(columnsAsText_, values)
    }
}