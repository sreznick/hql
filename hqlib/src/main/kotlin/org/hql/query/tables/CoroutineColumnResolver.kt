package org.hql.query.tables

import org.hql.hprof.heap.instances.coroutines.CoroutineRow

/**
 * Resolves logical column names to values of a coroutine row.
 *
 * Acts as a schema and access layer for coroutine table columns,
 * including derived and nested fields (e.g. parent.*, context fields)
 */
class CoroutineColumnResolver {

    // Mapping between logical column names and coroutine row fields.
    // This effectively defines the table schema and supported derived columns
    fun resolve(row: CoroutineRow, name: String): Any? =
        when (name) {
            "id" -> row.instance.id.toCompactHex()
            "type" -> row.type
            "state" -> row.state
            "parent" -> row.parent?.instance?.id?.toCompactHex()

            "job_type" -> row.contextInfo.job
            "dispatcher" -> row.contextInfo.dispatcher
            "name" -> row.contextInfo.name

            else -> resolveNested(row, name)
        }

    private fun resolveNested(row: CoroutineRow, name: String): Any? =
        if (name.startsWith("parent.")) {
            // Support nested access to parent coroutine fields using "parent.<field>" syntax
            val field = name.removePrefix("parent.")
            // comment for later removal: добавить обработку случая, когда родитель job
            (row.parent as? CoroutineRow)?.let {
                resolve(it, field)
            }
        } else {
            // Fallback to raw heap field access for advanced or experimental queries.
            // This allows inspecting internal coroutine fields not explicitly exposed as table columns
            row.instance[name]
        }
}