package org.hql.query.tables

import org.hql.hprof.heap.instances.coroutines.CoroutineRow
import org.hql.query.Cell
import org.hql.query.NullCell
import org.hql.query.StringCell

/**
 * Resolves logical column names to [Cell] values of a coroutine row.
 *
 * Acts as a schema and access layer for coroutine table columns,
 * including derived and nested fields (e.g. parent.*, context fields)
 */
class CoroutineColumnResolver {

    // Mapping between logical column names and coroutine row fields.
    // This effectively defines the table schema and supported derived columns
    fun resolve(row: CoroutineRow, name: String): Cell =
        when (name) {
            "id" -> row.instance.id.toCompactHex().toCell()
            "type" -> row.type.cell()
            "state" -> row.state.cell()
            "parent" -> row.parent?.instance?.id?.toCompactHex().toCell()

            "dispatcher" -> row.contextInfo.dispatcher.cell()
            "name" -> row.contextInfo.name.toCell()

            // carrier thread of a running coroutine; all null for a suspended one (on no thread)
            "thread" -> row.carrierThread?.let { (it.name ?: it.instance.id.toCompactHex()) }.toCell()
            // thread's heap id, matching the `id` column of the threads table
            "thread_id" -> row.carrierThread?.instance?.id?.toCompactHex().toCell()
            "thread_state" -> row.carrierThread?.state?.cell() ?: NullCell

            else -> resolveNested(row, name)
        }

    private fun resolveNested(row: CoroutineRow, name: String): Cell =
        if (name.startsWith("parent.")) {
            // Support nested access to parent coroutine fields using "parent.<field>" syntax
            val field = name.removePrefix("parent.")
            // comment for later removal: добавить обработку случая, когда родитель job
            (row.parent as? CoroutineRow)?.let { resolve(it, field) } ?: NullCell
        } else {
            // Fallback to raw heap field access for advanced or experimental queries.
            // This allows inspecting internal coroutine fields not explicitly exposed as table columns
            Cell.fromInstance(row.instance[name])
        }

    private fun String?.toCell(): Cell = if (this == null) NullCell else StringCell(this)
    private fun Enum<*>?.cell(): Cell = if (this == null) NullCell else StringCell(name)
}
