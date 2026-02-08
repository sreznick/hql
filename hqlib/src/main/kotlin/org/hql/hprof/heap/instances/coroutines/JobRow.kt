package org.hql.hprof.heap.instances.coroutines

import org.hql.hprof.heap.instances.Instance
import org.hql.hprof.heap.instances.coroutines.enums.JobType

// comment for later removal: минимальная реализация, пока используется только для работы с корутинами, у которых родитель - Job
data class JobRow(
    override val instance: Instance,
    val type: JobType,
) : CoroutineParentRow(instance)