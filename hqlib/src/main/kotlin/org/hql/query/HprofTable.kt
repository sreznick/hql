package org.hql.query

import org.hql.hprof.heap.BasicType
import org.hql.hprof.heap.Class
import org.hql.hprof.heap.Heap
import org.hql.hprof.heap.Instance
import org.hql.query.ast.FilterExpr
import kotlin.math.max

private fun repr(value: Any?): String {
    if (value == null) return "null"
    if (value is Instance && value.getType() == "java.lang.String") {
        val content = (value["value"] as List<Byte>).toByteArray().toString(Charsets.UTF_8)
        return "\"$content\""
    }
    return value.toString()
}

private fun <T: Comparable<T>> comparator(
    filter: FilterExpr.Comparison,
    literalParser: (String) -> T?
): (Instance) -> Boolean {
    val const = literalParser(filter.value)
        ?: throw RuntimeException("field of type is not comparable to constant ${filter.value}")
    return when (filter.operator) {
        "=" -> { instance ->
            (instance[filter.field] as T) == const
        }
        "!=" -> { instance ->
            (instance[filter.field] as T) != const
        }
        "<" -> { instance ->
            (instance[filter.field] as T) < const
        }
        "<=" -> { instance ->
            (instance[filter.field] as T) <= const
        }
        ">" -> { instance ->
            (instance[filter.field] as T) > const
        }
        ">=" -> { instance ->
            (instance[filter.field] as T) >= const
        }
        else -> throw RuntimeException("invalid operator: ${filter.operator}")
    }
}

private fun genericComparator(
    filter: FilterExpr.Comparison
): (Instance) -> Boolean = { instance ->
    val value = instance[filter.field]
    if (value is Instance && value.getType() == "java.lang.String") {
        val content = (value["value"] as List<Byte>).toByteArray().toString(Charsets.UTF_8)
        when (filter.operator) {
            "=" -> content == filter.value
            "!=" -> content != filter.value
            "<" -> content < filter.value
            "<=" -> content <= filter.value
            ">" -> content > filter.value
            ">=" -> content >= filter.value
            else -> throw RuntimeException("invalid operator: ${filter.operator}")
        }
    } else
        throw RuntimeException("comparing objects of type ${filter.field} is not supported")
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

    private fun constructFilter(filter: FilterExpr): (Instance) -> Boolean {
        when (filter) {
            is FilterExpr.Comparison -> {
                val field = cls.getInstanceFieldTypes().firstNotNullOfOrNull { (k, v) ->
                    if (k == filter.field) k to v else null
                } ?: throw RuntimeException("no such field: ${filter.field}")

                val type = field.second
                return when (type) {
                    BasicType.INT -> comparator(filter) { it.toIntOrNull() }
                    BasicType.SHORT -> comparator(filter) { it.toShortOrNull() }
                    BasicType.BYTE -> comparator(filter) { it.toByteOrNull() }
                    BasicType.CHAR -> comparator(filter) { it.toUByteOrNull() }
                    BasicType.LONG -> comparator(filter) { it.toLongOrNull() }
                    BasicType.FLOAT -> comparator(filter) { it.toFloatOrNull() }
                    BasicType.DOUBLE -> comparator(filter) { it.toDoubleOrNull() }
                    BasicType.BOOLEAN -> comparator(filter) { it.toBooleanStrictOrNull() }
                    BasicType.OBJECT -> genericComparator(filter)
                }
            }
            is FilterExpr.And -> {
                val left = constructFilter(filter.left)
                val right = constructFilter(filter.right)
                return { instance -> left(instance) && right(instance) }
            }
            is FilterExpr.Or -> {
                val left = constructFilter(filter.left)
                val right = constructFilter(filter.right)
                return { instance -> left(instance) || right(instance) }
            }
        }
    }

    private fun printTable(columns: List<String>, instances: List<Instance>) {
        val cols = columns.size
        val rows = instances.size
        val values = columns.map { field ->
            instances.map { instance ->
                repr(instance[field])
            }
        }
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

    fun select(columns: List<String>, filter: FilterExpr?, limit: Int) {
        val columns_ = columns.ifEmpty { fieldNames.toList() }

        val filterF = if (filter != null) constructFilter(filter) else { _ -> true }

        val filteredInstances = instances
            .filter(filterF)
            .take(limit)
        printTable(columns_, filteredInstances)
    }
}