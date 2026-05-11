package org.hql.query.tables

import org.hql.HQLQueryException
import org.hql.hprof.heap.Heap
import org.hql.hprof.heap.instances.coroutines.CoroutineRow
import org.hql.hprof.reader.coroutines.CoroutineHeapSearcher
import org.hql.query.BooleanCell
import org.hql.query.Cell
import org.hql.query.IntCell
import org.hql.query.Row
import org.hql.query.StringCell
import org.hql.query.expressions.BuiltinFunctions
import org.hql.query.expressions.BuiltinFunctions.requireSingleString

/**
 * Table implementation over coroutine data extracted from a heap dump
 */
class CoroutineTable(heap: Heap) : AbstractTable<CoroutineRow>() {

    private val resolver = CoroutineColumnResolver()

    override val baseColumns: List<String> = DEFAULT_COLUMNS

    // converted coroutine rows to internal format from found coroutines in the dump
    override val rows: List<CoroutineRow> = CoroutineHeapSearcher(heap).findAll()

    private val childrenIndex: Map<CoroutineRow, List<CoroutineRow>> =
        rows.mapNotNull { row -> row.parent?.let { it to row } }
            .groupBy({ it.first }) { it.second }

    private val rowsById: Map<String, CoroutineRow> =
        rows.associateBy { it.instance.id.toCompactHex() }

    // Memoize subtrees so a WHERE is_descendant_of(...) walks each subtree once across N rows,
    // not once per row evaluated.
    private val descendantsByRootId = mutableMapOf<String, Set<CoroutineRow>>()
    private val siblingsByRootId = mutableMapOf<String, Set<CoroutineRow>>()

    init {
        BuiltinFunctions.register("descendants_count") { row, _ ->
            val root = lookupRow(row)
            IntCell(descendantSetOf(root.instance.id.toCompactHex()).size.toLong())
        }
        BuiltinFunctions.register("is_descendant_of") { row, args ->
            val rootId = args.requireSingleString("is_descendant_of")
            BooleanCell(lookupRow(row) in descendantSetOf(rootId))
        }
        BuiltinFunctions.register("is_sibling_of") { row, args ->
            val rootId = args.requireSingleString("is_sibling_of")
            BooleanCell(lookupRow(row) in siblingSetOf(rootId))
        }
    }

    private fun descendantSetOf(rootId: String): Set<CoroutineRow> =
        descendantsByRootId.getOrPut(rootId) {
            val root = rowsById[rootId]
                ?: throw HQLQueryException("is_descendant_of: no coroutine with id $rootId")
            descendants(root).toSet()
        }

    private fun siblingSetOf(rootId: String): Set<CoroutineRow> =
        siblingsByRootId.getOrPut(rootId) {
            val root = rowsById[rootId]
                ?: throw HQLQueryException("is_sibling_of: no coroutine with id $rootId")
            siblings(root).toSet()
        }

    private fun children(row: CoroutineRow): List<CoroutineRow> = childrenIndex[row].orEmpty()

    private fun siblings(row: CoroutineRow): List<CoroutineRow> {
        val parent = row.parent ?: return emptyList()
        return children(parent).filter { it != row }
    }

    private fun descendants(root: CoroutineRow): List<CoroutineRow> {
        val result = mutableListOf<CoroutineRow>()
        val visited = mutableSetOf<CoroutineRow>()

        fun dfs(node: CoroutineRow) {
            if (!visited.add(node)) return
            for (child in children(node)) {
                result += child
                dfs(child)
            }
        }

        dfs(root)
        return result
    }

    override fun resolveCell(row: CoroutineRow, column: String): Cell = resolver.resolve(row, column)

    private fun lookupRow(row: Row): CoroutineRow {
        val idCell = row["id"] as? StringCell
            ?: throw HQLQueryException("expected a coroutine row (string id)")
        return rowsById[idCell.value]
            ?: throw HQLQueryException("row not found in coroutines table")
    }

    companion object {
        private val DEFAULT_COLUMNS = listOf(
            "id", "type", "state", "parent", "dispatcher", "name"
        )
    }
}
