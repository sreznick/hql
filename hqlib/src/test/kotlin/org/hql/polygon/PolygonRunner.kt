package org.hql.polygon

import org.hql.polygon.dumper.HeapDumper
import org.hql.polygon.scenario.PolygonScenario
import java.nio.file.Path
import kotlin.io.path.exists
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Исполнитель тестовых сценариев и оркестратор генерации дампов памяти.
 * Отвечает за инициализацию среды тестирования и кэширование файлов .hprof.
 */
class PolygonRunner(
    private val dumper: HeapDumper,
    private val dumpsDir: Path
) {
    private val log = KotlinLogging.logger {}

    /**
     * Запускает переданный сценарий, подготавливает состояние памяти и создает файл дампа.
     * Если файл дампа для этого сценария уже существует, повторный запуск пропускается для экономии времени.
     */
    fun runAndDump(scenario: PolygonScenario): Result<Path> = runCatching {
        val targetPath = dumpsDir.resolve("${scenario.scenarioName}.hprof")

        // Механизм кэширования: повторно дамп на диске не пересоздаем
        if (targetPath.exists()) {
            return@runCatching targetPath
        }

        log.info { "Starting scenario [${scenario.scenarioName}]..." }

        // Использование конструкции .use гарантирует вызов close() у сценария для очистки ресурсов
        scenario.use { activeScenario ->
            activeScenario.setupSync()
            log.info{"Memory state prepared. Capturing heap dump...."}

            val dumpResult = dumper.dump(targetPath, overwrite = false)
            dumpResult.getOrThrow()
        }
    }
}