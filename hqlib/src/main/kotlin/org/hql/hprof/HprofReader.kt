package org.hql.hprof

import org.netbeans.lib.profiler.heap.Field
import org.netbeans.lib.profiler.heap.Heap
import org.netbeans.lib.profiler.heap.HeapFactory
import org.netbeans.lib.profiler.heap.Instance
import org.netbeans.lib.profiler.heap.JavaClass
import org.netbeans.lib.profiler.heap.PrimitiveArrayInstance
import java.io.File
import java.lang.NullPointerException
import java.util.Locale
import java.util.Locale.getDefault
import kotlin.math.max

private val JavaClass.fieldsT
    get() = this.fields as List<Field>
private val JavaClass.instancesT
    get() = this.instances as List<Instance>

/* temp while #4 isn't merged */
sealed class FilterExpr {
    // Лист дерева: конкретное сравнение
    data class Comparison(val field: String, val operator: String, val value: String) : FilterExpr()

    // Узлы дерева: логика
    data class And(val left: FilterExpr, val right: FilterExpr) : FilterExpr()
    data class Or(val left: FilterExpr, val right: FilterExpr) : FilterExpr()
}

private fun repr(value: Any): String {
    if (value is PrimitiveArrayInstance) {
        if (value.javaClass.name == "char[]") {
            return (value.values as List<String>).joinToString(prefix = "\"", postfix = "\"", separator = "")
        }
        if (value.javaClass.name == "byte[]") {
            return (value.values as List<String>).joinToString(prefix = "\"", postfix = "\"", separator = "") {
                it.toInt().toChar().toString()
            }
        }
        return value.values.joinToString(prefix = "[", postfix = "]")
    } else if (value is Instance) {
        if (value.javaClass.name == "java.lang.String") {
            return repr(value.getValueOfField("value"))
        }
        return "(object ${value.javaClass.name})"
    }
    return value.toString()
}

private fun <T: Comparable<T>> comparer(
    filter: FilterExpr.Comparison,
    literalParser: (String) -> T?
): (Instance) -> Boolean {
    val const = literalParser(filter.value)
        ?: throw RuntimeException("field of type int is not comparable to constant ${filter.value}")
    return when (filter.operator) {
        "=" -> { instance ->
            (instance.getValueOfField(filter.field) as T) == const
        }
        "!=" -> { instance ->
            (instance.getValueOfField(filter.field) as T) != const
        }
        "<" -> { instance ->
            (instance.getValueOfField(filter.field) as T) < const
        }
        "<=" -> { instance ->
            (instance.getValueOfField(filter.field) as T) <= const
        }
        ">" -> { instance ->
            (instance.getValueOfField(filter.field) as T) > const
        }
        ">=" -> { instance ->
            (instance.getValueOfField(filter.field) as T) >= const
        }
        else -> throw RuntimeException("invalid operator: ${filter.operator}")
    }
}

class HprofTable(private val heap: Heap, className: String) {
    private val cls: JavaClass
    private val fieldNames: Set<String>
    private val instances: List<Instance>

    init {
        try {
            cls = heap.getJavaClassByName(className)
        } catch (_: NullPointerException) {
            throw RuntimeException("no such class: $className")
        }
        fieldNames = cls.fieldsT.map { it.name }.toSet()
        instances = cls.instancesT
    }

    private fun constructFilter(filter: FilterExpr): (Instance) -> Boolean {
        when (filter) {
            is FilterExpr.Comparison -> {
                val field = cls.fieldsT.firstOrNull { it.name == filter.field }
                    ?: throw RuntimeException("no such field: ${filter.field}")

                val type = field.type.name
                return when (type) {
                    "int" -> comparer(filter) { it.toIntOrNull() }
                    "short" -> comparer(filter) { it.toShortOrNull() }
                    "byte" -> comparer(filter) { it.toByteOrNull() }
                    "char" -> comparer(filter) { it.toUByteOrNull() }
                    "long" -> comparer(filter) { it.toLongOrNull() }
                    "float" -> comparer(filter) { it.toFloatOrNull() }
                    "double" -> comparer(filter) { it.toDoubleOrNull() }
                    "boolean" -> comparer(filter) { it.toBooleanStrictOrNull() }
                    "java.lang.String" -> comparer(filter) { it }
                    else -> throw RuntimeException("comparing variables of type $type is not supported")
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
                repr(instance.getValueOfField(field))
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
        val columns_ = if (columns == listOf("*"))
            fieldNames.toList()
        else columns

        val filterF = if (filter != null) constructFilter(filter) else { _ -> true }

        val filteredInstances = instances
            .filter(filterF)
            .take(limit)
        printTable(columns_, filteredInstances)
    }
}

class HprofReader(path: String) {
    val heap: Heap = HeapFactory.createHeap(File(path))

    init {
        if (heap.summary.totalAllocatedBytes == -1L) {
            throw RuntimeException("Failed to read hprof")
        }
    }

    fun query(
        targetClass: String,
        columns: List<String>,
        filter: FilterExpr? = null,
        limit: Int = Int.MAX_VALUE
    ) {
        val cls = HprofTable(heap, targetClass)
        cls.select(columns, filter, limit)
    }
}