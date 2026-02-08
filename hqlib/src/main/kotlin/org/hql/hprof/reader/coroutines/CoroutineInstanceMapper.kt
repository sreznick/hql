package org.hql.hprof.reader.coroutines

import org.hql.hprof.heap.instances.Instance
import org.hql.hprof.heap.instances.coroutines.CoroutineContextInfo
import org.hql.hprof.heap.instances.coroutines.CoroutineParentRow
import org.hql.hprof.heap.instances.coroutines.CoroutineRow
import org.hql.hprof.heap.instances.coroutines.JobRow
import org.hql.hprof.heap.instances.coroutines.enums.CoroutineState
import org.hql.hprof.heap.instances.coroutines.enums.CoroutineType
import org.hql.hprof.heap.instances.coroutines.enums.Dispatcher
import org.hql.hprof.heap.instances.coroutines.enums.JobType

/**
 * Maps raw heap instances related to coroutines and jobs into coroutine models
 */
object CoroutineInstanceMapper {

    fun getCoroutineRow(instance: Instance, clsName: String) = instance.buildCoroutineRow(clsName)

    private fun Instance.buildCoroutineRow(clsName: String) = CoroutineRow(
        instance = this,
        type = clsName.toCoroutineType(),
        state = (this["_state"] as? Instance).toCoroutineState(),
        parent = (this["_parentHandle"] as? Instance).toParentRow(),
        contextInfo = this.toContextInfo()
    )

    private fun Instance.buildJobRow(): JobRow? {
        val type = toJobType() ?: return null
        return JobRow(this, type)
    }

    private fun Instance.toJobType(): JobType? {
        return if (getClass().getName() == "kotlinx.coroutines.JobImpl") {
            JobType.JOB
        } else if (getClass().getName() == "kotlinx.coroutines.SupervisorJobImpl") {
            JobType.SUPERVISOR_JOB
        } else null
    }

    private fun Instance?.toParentRow(): CoroutineParentRow? {
        return this?.takeIf { it.getClass().getName() == "kotlinx.coroutines.ChildHandleNode" }
            ?.let { node ->
                val parent = node["job"] as? Instance ?: return null
                parent.buildJobRow() ?: parent.buildCoroutineRow(parent.getClass().getName())
            }
    }

    private fun Instance?.toCoroutineState(): CoroutineState {
        if (this == null) return CoroutineState.UNKNOWN

        return when (getClass().getName()) {
            "kotlinx.coroutines.ChildContinuation",
            "kotlinx.coroutines.CancellableContinuationImpl" ->
                CoroutineState.SUSPENDED

            "kotlinx.coroutines.Empty" -> CoroutineState.ACTIVE

            // comment for later removal: надо перепроверять, возможно это не 100% правда
            "kotlinx.coroutines.NodeList" -> (this["_prev"] as? Instance).toCoroutineState()

            $$"kotlinx.coroutines.JobSupport$Finishing" ->
                CoroutineState.WAITING_CHILDREN

            else -> CoroutineState.UNKNOWN
        }
    }

    private fun String.toCoroutineType(): CoroutineType {
        return when (this) {
            "kotlinx.coroutines.BlockingCoroutine" -> CoroutineType.BLOCKING
            "kotlinx.coroutines.LazyStandaloneCoroutine" -> CoroutineType.LAZY_STANDALONE
            "kotlinx.coroutines.internal.ScopeCoroutine" -> CoroutineType.SCOPE
            else -> CoroutineType.STANDALONE
        }
    }

    private fun Instance.toContextInfo(): CoroutineContextInfo {
        val context = this["context"] as? Instance ?: return CoroutineContextInfo()
        if (context.getClass().getName() == "kotlin.coroutines.EmptyCoroutineContext") {
            return CoroutineContextInfo()
        }

        val parent = (this["_parentHandle"] as? Instance).toParentRow()
        val job = if (parent is JobRow) {
            parent.type
        } else {
            null
        }
        var dispatcher: Dispatcher? = null
        var name: String? = null

        // recursive context traversal
        fun walk(node: Instance?) {
            if (node == null) return

            when (node.getClass().getName()) {
                "kotlin.coroutines.CombinedContext" -> {
                    val leftId = node["left"] as? Instance
                    val elementId = node["element"] as? Instance

                    leftId?.let { walk(it) }
                    elementId?.let { walk(it) }
                }

                // Dispatcher
                // comment for later removal: перепроверить все ли это реализации
                "kotlinx.coroutines.scheduling.DefaultIoScheduler",
                "kotlinx.coroutines.scheduling.DefaultScheduler" -> {
                    dispatcher = node.toDispatcher()
                }

                // CoroutineName
                "kotlinx.coroutines.CoroutineName" -> {
                    val nameInst = node["name"] as? Instance
                    name = nameInst.toString()
                }
            }
        }
        walk(context)
        return CoroutineContextInfo(job, dispatcher, name)
    }

    private fun Instance.toDispatcher(): Dispatcher {
        return when (this.getClass().getName()) {
            "kotlinx.coroutines.scheduling.DefaultIoScheduler" -> Dispatcher.IO
            else -> Dispatcher.DEFAULT
        }
    }
}