package org.hql.polygon.scenario

/**
 * Интерфейс, определяющий контракт для запуска контролируемых сценариев наполнения памяти кучи.
 * Наследуется от AutoCloseable для гарантированного освобождения системных ресурсов и потоков.
 */
interface PolygonScenario : AutoCloseable {
    val scenarioName: String
    /** Выполняет наполнение памяти объектами и блокирует выполнение до их полной инициализации */
    fun setupSync()
}