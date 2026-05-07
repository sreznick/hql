package org.hql.hprof.heap

import org.hql.ClassNotFoundException
import org.hql.hprof.reader.Hprof

class Heap(private val hprof: Hprof) {
    private val classes = hprof.classes.map { (id, value) ->
        val cls = Class(hprof, value)
        cls.name to cls
    }.toMap()

    fun getClassByName(name: String) = classes.getOrElse(name) {
        throw ClassNotFoundException(name)
    }
}