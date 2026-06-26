package org.hql.hprof.heap.instances.threads.enums

// JVMTI thread-state bits (jvmti.h / sun.misc.VM)
private const val RUNNABLE_BIT = 0x0004
private const val WAITING_INDEFINITELY = 0x0010
private const val WAITING_WITH_TIMEOUT = 0x0020
private const val BLOCKED_ON_MONITOR_ENTER = 0x0400

/**
 * JVM thread state, as exposed by [java.lang.Thread.State].
 *
 * NEW and TERMINATED are intentionally omitted: a heap dump only captures live threads, so a
 * thread that has not yet started (NEW) or has already finished (TERMINATED) is never present
 * with a catchable `threadStatus`.
 */
enum class ThreadState {
    RUNNABLE,
    BLOCKED,
    WAITING,
    TIMED_WAITING,
    UNKNOWN;

    companion object {
        /**
         * In a heap dump a Thread carries a raw `threadStatus` int (the JVMTI thread-state bitmask),
         * not a [java.lang.Thread.State] enum.
         * Reproduces the mapping the JDK uses in `sun.misc.VM.toThreadState`
         */
        fun fromThreadStatus(status: Int?): ThreadState {
            if (status == null) return UNKNOWN
            return when {
                status and RUNNABLE_BIT != 0 -> RUNNABLE
                status and BLOCKED_ON_MONITOR_ENTER != 0 -> BLOCKED
                status and WAITING_INDEFINITELY != 0 -> WAITING
                status and WAITING_WITH_TIMEOUT != 0 -> TIMED_WAITING
                else -> RUNNABLE
            }
        }
    }
}
