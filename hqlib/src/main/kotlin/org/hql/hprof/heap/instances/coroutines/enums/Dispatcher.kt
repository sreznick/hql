package org.hql.hprof.heap.instances.coroutines.enums

enum class Dispatcher {
    DEFAULT,
    IO
    // comment for later removal:
    // сложно в реализации пока, скорее всего для Dispatcher'а нужно отойти от enum и перейти к работе с потоками
    //MAIN,
    //UNCONFINED
}