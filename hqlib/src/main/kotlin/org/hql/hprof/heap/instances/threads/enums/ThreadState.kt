package org.hql.hprof.heap.instances.threads.enums

/**
 * JVM thread state, as exposed by [java.lang.Thread.State]
 */
enum class ThreadState {
    NEW,
    RUNNABLE,
    BLOCKED,
    WAITING,
    TIMED_WAITING,
    TERMINATED,
    UNKNOWN;

    companion object {
        // JVMTI thread-state bits (jvmti.h / sun.misc.VM)
        private const val ALIVE = 0x0001
        private const val TERMINATED_BIT = 0x0002
        private const val RUNNABLE_BIT = 0x0004
        private const val WAITING_INDEFINITELY = 0x0010
        private const val WAITING_WITH_TIMEOUT = 0x0020
        private const val BLOCKED_ON_MONITOR_ENTER = 0x0400

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
                status and TERMINATED_BIT != 0 -> TERMINATED
                status and ALIVE == 0 -> NEW
                else -> RUNNABLE
            }
        }
    }
}
