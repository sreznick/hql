package org.hql.query.tables

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import org.hql.HQLQueryException
import org.hql.hprof.heap.Heap
import org.hql.hprof.heap.Identifier
import org.hql.hprof.heap.instances.coroutines.CoroutineRow
import org.hql.hprof.heap.instances.coroutines.enums.CoroutineState
import org.hql.hprof.heap.instances.threads.enums.ThreadState
import org.hql.hprof.reader.coroutines.CoroutineHeapSearcher
import org.hql.hprof.reader.coroutines.CoroutineThreadLinker
import org.hql.hprof.reader.threads.ThreadHeapSearcher
import org.hql.query.BooleanCell
import org.hql.query.IntCell
import org.hql.query.Row
import org.hql.query.StringCell
import org.hql.query.expressions.BuiltinFunctions
import org.hql.query.expressions.BuiltinFunctions.requireSingleString

/**
 * Table implementation over coroutine data extracted from a heap dump
 */
class CoroutineTable(heap: Heap) : Table() {

    override val baseColumns: List<String> = DEFAULT_COLUMNS

    // Coroutine rows, each enriched with the live thread currently running it (if any).
    // The carrier thread is recovered by correlating stack-frame roots,
    // and lets a running coroutine surface its thread's JVM state which the coroutine's own state cannot show
    override val rows: List<CoroutineRow> = buildRows(heap)

    private fun buildRows(heap: Heap): List<CoroutineRow> {
        val base = CoroutineHeapSearcher(heap).findAll()
        val threadsById = ThreadHeapSearcher(heap).findAll().associateBy { it.instance.id }
        val carrierIdByCoroutineId = CoroutineThreadLinker(heap)
            .carrierThreadByCoroutine(base.map { it.instance.id }.toSet())
        return base.map { row ->
            row.copy(carrierThread = carrierIdByCoroutineId[row.instance.id]?.let { threadsById[it] })
        }
    }

    // keyed by the parent coroutine's instance id (value equality)
    private val childrenByParentId: Map<Identifier, List<CoroutineRow>> =
        rows.mapNotNull { row -> row.parent?.let { parent -> parent.instance.id to row } }
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
        // coroutine-level: the job is suspended at a suspension point
        BuiltinFunctions.register("is_suspended") { row, _ ->
            BooleanCell(lookupRow(row).state == CoroutineState.SUSPENDED)
        }
        // thread-level: the coroutine is running,
        // but its carrier thread is blocked on a monitor (synchronized / Object.wait) — invisible to the coroutine state machine,
        // only observable via the thread it occupies
        BuiltinFunctions.register("is_blocked") { row, _ ->
            BooleanCell(lookupRow(row).carrierThread?.state == ThreadState.BLOCKED)
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
        get() = childrenByParentId[instance.id].orEmpty()

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
            "id", "type", "state", "parent", "dispatcher", "name",
            "thread", "thread_id", "thread_state"
        )
    }
}
