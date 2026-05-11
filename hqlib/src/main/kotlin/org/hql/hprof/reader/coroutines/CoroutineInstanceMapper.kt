package org.hql.hprof.reader.coroutines

import org.hql.hprof.heap.instances.Instance
import org.hql.hprof.heap.instances.coroutines.CoroutineContextInfo
import org.hql.hprof.heap.instances.coroutines.CoroutineRow
import org.hql.hprof.heap.instances.coroutines.enums.CoroutineState
import org.hql.hprof.heap.instances.coroutines.enums.CoroutineType
import org.hql.hprof.heap.instances.coroutines.enums.Dispatcher

/**
 * Builds [CoroutineRow] models from raw heap instances.
 */
interface CoroutineMapper {
    operator fun invoke(instance: Instance.ObjectI, clsName: String): CoroutineRow
}

/**
 * Default [CoroutineMapper] that maps raw heap instances related to coroutines and jobs into coroutine models
 */
object CoroutineInstanceMapper : CoroutineMapper {

    override operator fun invoke(instance: Instance.ObjectI, clsName: String): CoroutineRow =
        instance.buildCoroutineRow(clsName)

    private fun Instance.ObjectI.buildCoroutineRow(clsName: String): CoroutineRow {
        val type = clsName.toCoroutineType()
        val stateInst = this["_state"] as? Instance.ObjectI
        val asCoroutine = !type.isJob()
        return CoroutineRow(
            instance = this,
            type = type,
            state = stateInst.toCoroutineState(asCoroutine),
            parent = (this["_parentHandle"] as? Instance.ObjectI).toParentRow(),
            contextInfo = if (asCoroutine) this.toContextInfo() else CoroutineContextInfo()
        )
    }

    private fun Instance.ObjectI?.toParentRow(): CoroutineRow? {
        return this?.takeIf { it.cls.name == "kotlinx.coroutines.ChildHandleNode" }
            ?.let { node ->
                val parent = node["job"] as? Instance.ObjectI ?: return null
                parent.buildCoroutineRow(parent.cls.name)
            }
    }

    private fun Instance.ObjectI?.toCoroutineState(asCoroutine: Boolean): CoroutineState {
        if (this == null) return CoroutineState.UNKNOWN

        return when (cls.name) {
            "kotlinx.coroutines.ChildContinuation",
            "kotlinx.coroutines.CancellableContinuationImpl" ->
                if (asCoroutine) CoroutineState.SUSPENDED else CoroutineState.WAITING_CHILDREN

            "kotlinx.coroutines.Empty" -> CoroutineState.ACTIVE

            // comment for later removal: надо перепроверять, возможно это не 100% правда
            "kotlinx.coroutines.NodeList",
            $$"kotlinx.coroutines.JobSupport$ChildCompletion" ->
                (this["_prev"] as? Instance.ObjectI).toCoroutineState(asCoroutine)

            "kotlinx.coroutines.ChildHandleNode" -> {
                val child = this["childJob"] as? Instance.ObjectI
                val childState = child?.get("_state") as? Instance.ObjectI
                val childIsJob = child?.cls?.name?.toCoroutineType()?.isJob() == true
                childState.toCoroutineState(asCoroutine && !childIsJob)
            }

            $$"kotlinx.coroutines.JobSupport$Finishing" -> CoroutineState.WAITING_CHILDREN

            else -> CoroutineState.UNKNOWN
        }
    }

    private fun String.toCoroutineType(): CoroutineType = when (this) {
        "kotlinx.coroutines.BlockingCoroutine" -> CoroutineType.BLOCKING
        "kotlinx.coroutines.LazyStandaloneCoroutine" -> CoroutineType.LAZY_STANDALONE
        "kotlinx.coroutines.internal.ScopeCoroutine" -> CoroutineType.SCOPE
        "kotlinx.coroutines.JobImpl" -> CoroutineType.JOB
        "kotlinx.coroutines.SupervisorJobImpl" -> CoroutineType.SUPERVISOR_JOB
        else -> CoroutineType.STANDALONE
    }

    private fun Instance.ObjectI.toContextInfo(): CoroutineContextInfo {
        val context = this["context"] as? Instance.ObjectI ?: return CoroutineContextInfo()
        if (context.cls.name == "kotlin.coroutines.EmptyCoroutineContext") {
            return CoroutineContextInfo()
        }
        var dispatcher: Dispatcher? = null
        var name: String? = null

        // recursive context traversal
        fun walk(node: Instance.ObjectI?) {
            if (node == null) return

            when (node.cls.name) {
                "kotlin.coroutines.CombinedContext" -> {
                    val leftId = node["left"] as? Instance.ObjectI
                    val elementId = node["element"] as? Instance.ObjectI

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
                    val nameInst = node["name"]
                    name = nameInst.toString()
                }
            }
        }
        walk(context)
        return CoroutineContextInfo(dispatcher, name)
    }

    private fun Instance.ObjectI.toDispatcher(): Dispatcher = when (cls.name) {
        "kotlinx.coroutines.scheduling.DefaultIoScheduler" -> Dispatcher.IO
        else -> Dispatcher.DEFAULT
    }
}