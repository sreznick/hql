package org.hql.polygon.scenario

/**
 * Имитация внешнего независимого Java/Kotlin приложения.
 * Запускается в отдельной JVM с помощью ProcessBuilder для тестирования внепроцессного анализа памяти.
 */
object ExternalTargetApp {
    @JvmStatic
    fun main(args: Array<String>) {
        // Создаем массив уникальных маркеров-строк для последующего поиска их в дампе кучи
        val markerStrings = Array(100) {
            "HQL_EXTERNAL_TARGET_MARKER_STRING_$it"
        }

        // Отправляем сигнал готовности в стандартный вывод.
        // Управляющий тест считает эту строку и начнет процедуру снятия дампа.
        println("READY")

        try {
            // Блокируем поток выполнения, удерживая приложение в памяти до принудительного завершения тестом
            Thread.sleep(Long.MAX_VALUE)
        } catch (e: InterruptedException) {
            // Завершаем выполнение при получении сигнала прерывания
        }

        // Печатаем размер массива, чтобы предотвратить оптимизацию JIT (Dead Code Elimination) и удаление данных GC
        println(markerStrings.size)
    }
}