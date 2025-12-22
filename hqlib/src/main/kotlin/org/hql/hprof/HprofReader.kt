package org.hql.hprof

import org.netbeans.lib.profiler.heap.Heap
import org.netbeans.lib.profiler.heap.HeapFactory
import org.netbeans.lib.profiler.heap.HeapSummary
import java.io.File

class HprofReader(path: String) {
    val heap: Heap = HeapFactory.createHeap(File(path))

    fun summary() {
        val summary: HeapSummary = heap.summary
        println("Total instances: " + summary.totalLiveInstances)
        println("Total bytes: " + summary.totalLiveBytes)
        println("Time: " + summary.time)
        println("GC Roots: " + heap.gcRoots.size)
        println("Total classes: " + heap.allClasses.size)
    }
}