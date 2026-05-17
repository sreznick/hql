package org.hql.hprof.heap.instances.coroutines

import org.hql.hprof.heap.instances.Instance
import org.hql.hprof.heap.instances.coroutines.enums.CoroutineState
import org.hql.hprof.heap.instances.coroutines.enums.CoroutineType
import org.hql.query.Cell
import org.hql.query.NullCell
import org.hql.query.Row
import org.hql.query.StringCell

data class CoroutineRow(
    override val instance: Instance.ObjectI,
    val type: CoroutineType,
    val state: CoroutineState,
    val parent: CoroutineParentRow?,
    val contextInfo: CoroutineContextInfo
) : CoroutineParentRow(instance), Row {
    override fun get(column: String): Cell =
        when (column) {
            "id" -> instance.id.toCompactHex().toCell()
            "type" -> type.cell()
            "state" -> state.cell()
            "parent" -> parent?.instance?.id?.toCompactHex().toCell()

            "job_type" -> contextInfo.job.cell()
            "dispatcher" -> contextInfo.dispatcher.cell()
            "name" -> contextInfo.name.toCell()

            else -> resolveNested(column)
        }

    private fun resolveNested(name: String): Cell =
        if (name.startsWith("parent.")) {
            // Support nested access to parent coroutine fields using "parent.<field>" syntax
            val field = name.removePrefix("parent.")
            // comment for later removal: добавить обработку случая, когда родитель job
            (parent as? CoroutineRow)?.let { it[field] } ?: NullCell
        } else {
            // Fallback to raw heap field access for advanced or experimental queries.
            // This allows inspecting internal coroutine fields not explicitly exposed as table columns
            Cell.fromInstance(instance[name])
        }

    private fun String?.toCell(): Cell = if (this == null) NullCell else StringCell(this)
    private fun Enum<*>?.cell(): Cell = if (this == null) NullCell else StringCell(name)
}