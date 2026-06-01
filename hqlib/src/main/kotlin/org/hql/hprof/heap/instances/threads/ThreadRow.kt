package org.hql.hprof.heap.instances.threads

import org.hql.hprof.heap.instances.Instance
import org.hql.hprof.heap.instances.threads.enums.ThreadState
import org.hql.query.BooleanCell
import org.hql.query.Cell
import org.hql.query.IntCell
import org.hql.query.NullCell
import org.hql.query.Row
import org.hql.query.StringCell

/**
 * A live thread extracted from the heap dump.

 */
data class ThreadRow(
    val instance: Instance.ObjectI,
    val className: String,
    val name: String?,
// is the JVM-level thread state (e.g. BLOCKED), decoded from the Thread's `threadStatus`
    val state: ThreadState,
    val daemon: Boolean?,
    val priority: Int?,
    val tid: Long?
) : Row {
    override fun get(column: String): Cell =
        when (column) {
            "id" -> StringCell(instance.id.toCompactHex())
            "name" -> name?.let(::StringCell) ?: NullCell
            "state" -> StringCell(state.name)
            "daemon" -> daemon?.let(::BooleanCell) ?: NullCell
            "priority" -> priority?.let { IntCell(it.toLong()) } ?: NullCell
            "tid" -> tid?.let(::IntCell) ?: NullCell
            "class" -> StringCell(className)

            // Fallback to raw heap field access for fields not exposed as table columns.
            else -> Cell.fromInstance(instance[column])
        }
}
