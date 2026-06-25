package org.hql.polygon.dumper

import java.lang.management.ManagementFactory
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.absolutePathString
import com.sun.management.HotSpotDiagnosticMXBean

/**
 * Общий интерфейс для создания дампов кучи (heap dump) в формате .hprof.
 */
interface HeapDumper {
    /**
     * Создает дамп памяти и сохраняет его по указанному пути.
     * Если файл уже существует и флаг [overwrite] равен false, повторная генерация пропускается.
     */
    fun dump(targetPath: Path, overwrite: Boolean = false): Result<Path>
}

/**
 * Реализация дампера для снятия снимка памяти внутри текущего процесса JVM через JMX API.
 */
class JmxHeapDumper(
    private val dumpOnlyLiveObjects: Boolean = true
) : HeapDumper {

    override fun dump(targetPath: Path, overwrite: Boolean): Result<Path> = runCatching {
        if (targetPath.exists() && !overwrite) {
            return@runCatching targetPath
        }

        // Получаем доступ к диагностическому MBean для работы с HotSpot JVM
        val server = ManagementFactory.getPlatformMBeanServer()
        val mxBean = ManagementFactory.newPlatformMXBeanProxy(
            server,
            "com.sun.management:type=HotSpotDiagnostic",
            HotSpotDiagnosticMXBean::class.java
        )

        if (targetPath.exists()) {
            targetPath.toFile().delete()
        }

        mxBean.dumpHeap(targetPath.absolutePathString(), dumpOnlyLiveObjects)
        targetPath
    }
}

/**
 * Реализация дампера для снятия снимка памяти внешнего JVM-процесса по его PID.
 * Использует системную утилиту jcmd.
 */
class ExternalProcessDumper(
    private val pid: Long,
    private val dumpOnlyLiveObjects: Boolean = true
) : HeapDumper {

    override fun dump(targetPath: Path, overwrite: Boolean): Result<Path> = runCatching {
        if (targetPath.exists() && !overwrite) {
            return@runCatching targetPath
        }

        if (targetPath.exists()) {
            targetPath.toFile().delete()
        }

        // Запуск системного процесса jcmd для генерации дампа целевой JVM
        val process = ProcessBuilder(
            "jcmd",
            pid.toString(),
            "GC.heap_dump",
            if (dumpOnlyLiveObjects) "-all=false" else "-all=true",
            targetPath.absolutePathString()
        ).start()

        val exitCode = process.waitFor()
        if (exitCode != 0) {
            // Читаем поток ошибок и закрываем стрим через .use
            val errorMsg = process.errorStream.bufferedReader().readText()
            throw RuntimeException("jcmd failed with exit code $exitCode: $errorMsg")
        }

        targetPath
    }
}