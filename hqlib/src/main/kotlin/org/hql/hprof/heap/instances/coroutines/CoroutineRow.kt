package org.hql.hprof.heap.instances.coroutines

import org.hql.hprof.heap.instances.Instance
import org.hql.hprof.heap.instances.coroutines.enums.CoroutineState
import org.hql.hprof.heap.instances.coroutines.enums.CoroutineType
import org.hql.query.Cell
import org.hql.query.NullCell
import org.hql.query.Row
import org.hql.query.StringCell
import org.hql.hprof.heap.instances.threads.ThreadRow

data class CoroutineRow(
    val instance: Instance.ObjectI,
    val type: CoroutineType,
    val state: CoroutineState,
    val parent: CoroutineRow?,
    val contextInfo: CoroutineContextInfo,
    // The live thread currently executing this coroutine, or null when it is suspended (on no
    // thread). Populated by the table after correlating frame roots; carries the JVM-level state
    // (e.g. BLOCKED on a monitor) that the coroutine's own state cannot express.
    val carrierThread: ThreadRow? = null
) : Row {
    override fun get(column: String): Cell =
        when (column) {
            "id" -> instance.id.toCompactHex().toCell
            "type" -> type.toCell
            "state" -> state.toCell
            "parent" -> parent?.instance?.id?.toCompactHex().toCell

            "dispatcher" -> contextInfo.dispatcher.toCell
            "name" -> contextInfo.name.toCell

            // carrier thread of a running coroutine; all null for a suspended one (on no thread)
            "thread" -> carrierThread?.let { it.name ?: it.instance.id.toCompactHex() }.toCell
            // thread's heap id, matching the `id` column of the threads table
            "thread_id" -> carrierThread?.instance?.id?.toCompactHex().toCell
            "thread_state" -> carrierThread?.state.toCell

            else -> {
                // Support nested access to parent coroutine fields using "parent.<field>" syntax
                if (column.startsWith("parent.")) {
                    resolveNested(column)
                } else {
                    // Fallback to raw heap field access for advanced or experimental queries.
                    // This allows inspecting internal coroutine fields not explicitly exposed as table columns
                    Cell.fromInstance(instance[column])
                }
            }
        }

    private fun resolveNested(name: String): Cell {
        val field = name.removePrefix("parent.")
        // comment for later removal: добавить обработку случая, когда родитель job
        return parent?.get(field) ?: NullCell
    }


    private val String?.toCell: Cell
        get() = this?.let { StringCell(this) } ?: NullCell
    private val Enum<*>?.toCell: Cell
        get() = this?.let { StringCell(name) } ?: NullCell
}