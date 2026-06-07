package org.hql.query.tables

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import org.hql.HQLQueryException
import org.hql.hprof.heap.Heap
import org.hql.hprof.heap.instances.coroutines.CoroutineRow
import org.hql.hprof.reader.coroutines.CoroutineHeapSearcher
import org.hql.query.BooleanCell
import org.hql.query.IntCell
import org.hql.query.Row
import org.hql.query.StringCell
import org.hql.query.expressions.BuiltinFunctions

/**
 * Table implementation over coroutine data extracted from a heap dump
 */
class CoroutineTable(heap: Heap) : Table() {
    override val name = "coroutines"

    override val baseColumns: List<String> = DEFAULT_COLUMNS

    // converted coroutine rows to internal format from found coroutines in the dump
    override val rows: List<CoroutineRow> = CoroutineHeapSearcher(heap).findAll()

    private val childrenIndex: Map<CoroutineRow, List<CoroutineRow>> =
        rows.mapNotNull { row -> row.parent?.let { it to row } }
            .groupBy({ it.first }) { it.second }

    private val rowsById: Map<String, CoroutineRow> =
        rows.associateBy { it.instance.id.toCompactHex() }

    // Memoize subtrees so a WHERE is_descendant_of(...) walks each subtree once across N rows,
    // not once per row evaluated. Bounded to avoid OOME on large dumps with many distinct roots
    private val descendantsByRootId: Cache<String, Set<CoroutineRow>> =
        CacheBuilder.newBuilder().maximumSize(SUBTREE_CACHE_MAX_SIZE).build()
    private val siblingsByRootId: Cache<String, Set<CoroutineRow>> =
        CacheBuilder.newBuilder().maximumSize(SUBTREE_CACHE_MAX_SIZE).build()

    init {
        BuiltinFunctions.register("descendants_count") {
            val root = lookupRow(row)
            IntCell(descendantSetOf(root.instance.id.toCompactHex()).size.toLong())
        }
        BuiltinFunctions.register("is_descendant_of") {
            val rootId = singleStringArgument()
            BooleanCell(lookupRow(row) in descendantSetOf(rootId))
        }
        BuiltinFunctions.register("is_sibling_of") {
            val rootId = singleStringArgument()
            BooleanCell(lookupRow(row) in siblingSetOf(rootId))
        }
    }

    private fun descendantSetOf(rootId: String): Set<CoroutineRow> =
        descendantsByRootId.get(rootId) {
            val root = rowsById[rootId]
                ?: throw HQLQueryException("is_descendant_of: no coroutine with id $rootId")
            descendants(root).toSet()
        }

    private fun siblingSetOf(rootId: String): Set<CoroutineRow> =
        siblingsByRootId.get(rootId) {
            val root = rowsById[rootId]
                ?: throw HQLQueryException("is_sibling_of: no coroutine with id $rootId")
            root.siblings.toSet()
        }

    private val CoroutineRow.children: List<CoroutineRow>
        get() = childrenIndex[this].orEmpty()

    private val CoroutineRow.siblings: List<CoroutineRow>
        get() = parent?.children?.filter { it !== this }.orEmpty()

    private fun descendants(root: CoroutineRow): List<CoroutineRow> {
        val result = mutableListOf<CoroutineRow>()
        val visited = mutableSetOf<CoroutineRow>()

        fun dfs(node: CoroutineRow) {
            if (!visited.add(node)) return
            for (child in node.children) {
                result += child
                dfs(child)
            }
        }

        dfs(root)
        return result
    }

    private fun lookupRow(row: Row): CoroutineRow {
        val idCell = row["id"] as? StringCell
            ?: throw HQLQueryException("expected a coroutine row (string id)")
        return rowsById[idCell.value]
            ?: throw HQLQueryException("row not found in coroutines table")
    }

    companion object {
        private const val SUBTREE_CACHE_MAX_SIZE = 1024L

        private val DEFAULT_COLUMNS = listOf(
            "id", "type", "state", "parent", "dispatcher", "name"
        )
    }
}
