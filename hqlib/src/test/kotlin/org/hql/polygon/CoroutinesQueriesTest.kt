package org.hql.polygon

import org.hql.hprof.heap.Heap
import org.hql.hprof.reader.HprofReader
import org.hql.polygon.dumper.JmxHeapDumper
import org.hql.polygon.scenario.CoroutinesScenario
import org.hql.query.Database
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.inputStream

/**
 * Тесты для проверки запросов к таблицам состояний корутин.
 * Базируются на дампе памяти, полученном из CoroutinesScenario.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CoroutinesQueriesTest {

    // Ленивая инициализация базы данных HQL на основе сгенерированного дампа кучи
    private val database: Database by lazy {
        val dumpsDir = Path.of("build/polygon-dumps").apply { createDirectories() }
        val runner = PolygonRunner(JmxHeapDumper(), dumpsDir)
        val hprofPath = runner.runAndDump(CoroutinesScenario()).getOrThrow()

        Database(Heap(HprofReader(hprofPath.inputStream()).getHprof()))
    }

    @Test
    @Disabled("TODO: Enable after PR #25 is merged. Tests CoroutineThreadLinker and threads table.")
    fun `testCoroutinesState - should find suspended and blocked coroutines`() {
        // Метод заблокирован до влития PR #25 (связывание корутин с потоками).
        // Пример ожидаемой логики после обновления:
        // val result = database.query("SELECT name, is_suspended() FROM coroutines WHERE name LIKE '%Polygon%'")
        // assertTrue(result.contains("Suspended-Polygon-1"))
    }
}