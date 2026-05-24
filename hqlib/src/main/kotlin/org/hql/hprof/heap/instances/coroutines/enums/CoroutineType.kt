package org.hql.hprof.heap.instances.coroutines.enums

enum class CoroutineType {
    // comment for later removal: можно расширить
    BLOCKING,
    STANDALONE,
    LAZY_STANDALONE,
    SCOPE,
    JOB,
    SUPERVISOR_JOB;

    fun isJob() = this == JOB || this == SUPERVISOR_JOB
}