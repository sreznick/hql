package org.hql.query.tables

import org.hql.hprof.heap.Heap
import org.hql.hprof.heap.instances.coroutines.CoroutineRow
import org.hql.hprof.reader.coroutines.CoroutineHeapSearcher
import org.hql.query.Table
import org.hql.query.expressions.Expression
import org.hql.query.printer.CoroutineTablePrinter

/**
 * Table implementation over coroutine data extracted from a heap dump
 */
class CoroutineTable(heap: Heap) : Table {
    // converted coroutine rows to internal format from found coroutines in the dump
    private val rows = CoroutineHeapSearcher(heap).findAll()

    private val printer = CoroutineTablePrinter()

    private val resolver = CoroutineColumnResolver()

    override fun select(
        columns: List<Expression>,
        columnNames: List<String>,
        filter: Expression?,
        sort: Expression?,
        sortDescending: Boolean,
        limit: Int?,
        offset: Int?
    ) {
        var processedRows = rows

        filter?.let {
            processedRows = rows.filter { row ->
                // eval returns null when the expression does not match.
                // any non-null value is treated as a successful predicate
                filter.eval { column ->
                    resolver.resolve(row, column)
                } != null
            }
        }

        sort?.let {
            val selector = sort.buildSortSelector(resolver::resolve)
            processedRows =
                if (sortDescending) processedRows.sortedByDescending(selector)
                else processedRows.sortedBy(selector)
        }

        offset?.let {
            processedRows = processedRows.drop(offset)
        }

        limit?.let {
            processedRows = processedRows.take(limit)
        }

        printer.print(processedRows, columnNames, resolver::resolve)
    }

    //comment for later removal: сейчас не работают and и or из-за неправильных приоритетов в парсере
    // return null value, if expression does not match
    // any non-null value means "match"
    fun Expression.eval(resolve: (String) -> Any?): Any? {
        return when (this) {
            is Expression.And -> {
                // both sides must be non-null
                left.eval(resolve) ?: return null
                right.eval(resolve) ?: return null
                true
            }

            is Expression.Or -> {
                // first non-null result wins
                left.eval(resolve) ?: return right.eval(resolve)
            }

            is Expression.Comparison -> {
                val l = resolve((left as Expression.Field).field).toString()
                val r = (right as Expression.Literal).value
                if (l == r) l else null
            }

            is Expression.Field -> resolve(field)
            is Expression.Literal -> value

            else -> error("Unsupported expression: $this")
        }
    }

    // Builds a selector function for sorting rows.
    // The evaluated value must be Comparable, otherwise sorting is not possible
    private fun Expression.buildSortSelector(
        resolve: (CoroutineRow, String) -> Any?
    ): (CoroutineRow) -> Comparable<Any?> =
        { row ->
            // ORDER BY expression is evaluated per row using the same resolution
            // mechanism as filtering to keep semantics consistent
            val value = eval { column ->
                resolve(row, column)
            }

            value as? Comparable<Any?>
                ?: error("ORDER BY expression is not comparable: $this")
        }
}