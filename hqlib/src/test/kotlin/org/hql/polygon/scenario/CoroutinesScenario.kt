package org.hql.polygon.scenario

import kotlinx.coroutines.*

/**
 * Сценарий для проверки анализа корутин и их связывания с физическими потоками JVM.
 * Моделирует приостановленные корутины и корутины, заблокировавшие несущие потоки.
 */
class CoroutinesScenario : PolygonScenario {
    override val scenarioName = "coroutines_threads"

    // Собственный CoroutineScope для контроля жизненного цикла сценария и очистки памяти
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Volatile
    private var isBlockedReady = false

    override fun setupAndWaitForReady() {
        // Корутин №1: Имитирует стандартное приостановленное состояние (Suspended) через delay
        scope.launch(CoroutineName("Suspended-Polygon-1")) {
            delay(10_000_000) // Висит в памяти
        }

        // Корутин №2: Имитирует блокировку несущего потока (Carrier Thread) монитором/синхронизацией
        val lock = Object()
        scope.launch(CoroutineName("Blocked-Polygon-2")) {
            synchronized(lock) {
                isBlockedReady = true
                Thread.sleep(10_000_000)
            }
        }

        // Ожидаем, пока корутин №2 гарантированно войдет в заблокированную секцию
        while (!isBlockedReady) {
            Thread.sleep(50)
        }
        // Пауза для стабилизации состояний планировщика корутин в памяти
        Thread.sleep(500)
    }

    override fun close() {
        // Принудительно отменяем все запущенные корутины во избежание утечки потоков в тестовом окружении
        scope.cancel()
    }
}