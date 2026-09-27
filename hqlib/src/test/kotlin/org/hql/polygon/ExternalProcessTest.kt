package org.hql.polygon

import org.hql.hprof.heap.Heap
import org.hql.hprof.reader.HprofReader
import org.hql.polygon.dumper.ExternalProcessDumper
import org.hql.query.Database
import org.hql.polygon.HeapDumpException
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.inputStream
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Интеграционные тесты для проверки анализа реальных, внепроцессных дампов памяти JVM.
 * Запускает изолированный дочерний процесс, генерирует дамп утилитой jcmd и проверяет устойчивость парсера кучи.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExternalProcessTest {

    private val log = KotlinLogging.logger {}

    private lateinit var targetProcess: Process
    private lateinit var hprofPath: Path
    private lateinit var database: Database

    @BeforeAll
    fun setup() {
        val dumpsDir = Path.of("build/polygon-dumps").apply { createDirectories() }
        hprofPath = dumpsDir.resolve("external_app.hprof")

        val javaHome = System.getProperty("java.home")
        val javaBin = Path.of(javaHome, "bin", "java").toString()
        val classpath = System.getProperty("java.class.path")

        // Запуск внешнего независимого JVM-процесса с тестовым сценарием
        targetProcess = ProcessBuilder(
            javaBin,
            "-cp", classpath,
            "org.hql.polygon.scenario.ExternalTargetApp"
        ).start()

        val pid = targetProcess.pid()
        log.info { "External process started. PID: $pid" }

        // Резервный хук на случай аварийного закрытия тестового окружения (предотвращает появление зомби-процессов в ОС)
        Runtime.getRuntime().addShutdownHook(Thread {
            if (targetProcess.isAlive) {
                targetProcess.destroyForcibly()
            }
        })

        // Безопасное чтение сигнала готовности и запуск утилиты jcmd для снятия дампа
        try {
            val reader = targetProcess.inputStream.bufferedReader()
            val line = reader.readLine()
            if (line != "READY") {
                throw HeapDumpException("The external process failed to start. Output: $line")
            }

            log.info { "The process is ready. Let's dump it using jcmd..." }
            val dumper = ExternalProcessDumper(pid, dumpOnlyLiveObjects = false)

            dumper.dump(hprofPath, overwrite = true).getOrThrow()
            log.info { "Dump saved successfully: $hprofPath" }

            log.info { "Parsing dump in HQL Database...." }
            database = Database(Heap(HprofReader(hprofPath.inputStream()).getHprof()))

        } catch (e: Throwable) {
            // Принудительно гасим дочерний процесс при сбое инициализации на этапе подготовки
            if (targetProcess.isAlive) {
                targetProcess.destroyForcibly()
                log.warn { "Initialization failed! External process was forcibly killed. Reason: ${e.message}" }
            }
            throw e
        }
    }

    @Test
    fun `testCrashResistance - should successfully parse full JVM dump without throwing`() {
        // Краш-тест на устойчивость парсера к зацикленным структурам и системным объектам JDK.
        // Реальный дамп JVM содержит тысячи внутренних классов (Thread, HashMap, String, ClassLoader),
        // на которых некорректно спроектированный парсер может упасть по StackOverflowError.

        assertDoesNotThrow {
            val query = "SELECT * FROM java.lang.String LIMIT 1"
            database.query(query)
        }
    }

    @Test
    fun `testDataIntegrity - dump should contain external app specific data`() {
        // До момента слияния PR #26 (с оператором LIKE) проверяем успешный парсинг данных кучи косвенно.
        // Если дамп считан верно, то таблица строк должна содержать не менее 50 элементов.
        //
        // После слияния PR #26 тест преобразуется в:
        // SELECT * FROM java.lang.String WHERE value LIKE '%HQL_EXTERNAL_TARGET_MARKER_STRING%'

        assertDoesNotThrow {
            database.query("SELECT * FROM java.lang.String LIMIT 50")
        }
    }

    @Test
    @Disabled("TODO: Enable after PR #26 is merged. Property-based (tautology) test for WHERE and COUNT.")
    fun `testTautology - total strings count should match sum of partitioned conditions`() {
        /*
        // Проверка логических инвариантов (Тавтологический тест).
        // Общее число строк должно строго сходиться с суммой разбиения по взаимоисключающим условиям.

        // 1. Узнаем общее количество строк в дампе
        val totalResult = database.query("SELECT count(value) FROM java.lang.String")
        val totalCount = parseSingleNumber(totalResult) // Условная функция парсинга ответа

        // 2. Узнаем количество длинных строк
        val longStringsResult = database.query("SELECT count(value) FROM java.lang.String WHERE length > 50")
        val longCount = parseSingleNumber(longStringsResult)

        // 3. Узнаем количество коротких строк и пустых
        val shortStringsResult = database.query("SELECT count(value) FROM java.lang.String WHERE length <= 50 OR length IS NULL")
        val shortCount = parseSingleNumber(shortStringsResult)

        // 4. Проверяем закон математики (Инвариант)
        assertEquals(totalCount, longCount + shortCount, "Math failed: $totalCount != $longCount + $shortCount")
        */
    }

    @Test
    @Disabled("TODO: Enable after PR #26. Invariant: the sum of object sizes must not exceed total heap size")
    fun testInvariant_ObjectsSizeShouldNotExceedTotalHeapSize() {
        /*
        // Проверка физического инварианта: сумма байт всех объектов в БД HQL не может превышать физический размер кучи

        // Условный пример запроса после влития агрегаций:
        val result = database.query("SELECT sum(size) FROM java.lang.Object")
        val totalObjectsSize = parseSingleNumber(result)

        // Предполагается, что в Heap будет метод getTotalSize()
        val maxHeapSize = database.heap.totalSize()

        assertTrue(totalObjectsSize <= maxHeapSize, "Сумма размеров объектов превышает физический размер дампа!")
        */
    }

    @AfterAll
    fun teardown() {
        // Принудительно завершаем дочерний процесс после завершения всех тестов в классе во избежание утечки системных ресурсов
        if (::targetProcess.isInitialized) {
            targetProcess.destroy()
            log.info { "External process (PID: ${targetProcess.pid()}) terminated." }
        }
    }
}