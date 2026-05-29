package org.hql.query

import com.charleskorn.kaml.MultiLineStringStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlMultiLineStringStyle
import kotlinx.serialization.Serializable
import org.hql.hprof.heap.Heap
import org.hql.hprof.reader.HprofReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.walk
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.fail

@Serializable
private data class QueryCase(
    val query: String,
    @YamlMultiLineStringStyle(MultiLineStringStyle.Literal)
    val expected: String = ""
)

@Serializable
private data class QueryExpectations(val queries: List<QueryCase>)

/**
 * Replays the same launches shown in the PR #13 / #20 testing screenshots
 * (App.kt running `<dump> "<query>"`) against committed expected outputs.
 *
 * For each `<name>.expected.yaml` under `hqlib/src/test/resources/expected/`,
 * opens `examples/dumps/01-endless-list-grow/<name>.hprof`, runs every listed
 * query through [Database.query], captures the printed table, and fails on
 * mismatches with a unified report.
 *
 * To regenerate baselines after intentional output changes, set the env var
 * `HQL_REGEN_EXPECTED=1` and rerun this test — captured outputs are written
 * back into the yaml in place of the recorded `expected` field, and the test
 * passes without comparing.
 */
class QueryE2ETest {

    private val repoRoot: Path = run {
        val resourceDir = File(
            javaClass.classLoader.getResource(".")?.file
                ?: error("unable to locate test resources dir")
        ).absoluteFile.toPath()
        generateSequence(resourceDir) { it.parent }
            .firstOrNull { (it / ".git").isDirectory() && (it / "examples" / "dumps").isDirectory() }
            ?: error("unable to find repo root")
    }

    private val dumpsDir: Path = repoRoot / "examples" / "dumps" / "01-endless-list-grow"

    private val expectationsDir: Path =
        repoRoot / "hqlib" / "src" / "test" / "resources" / "expected"

    private val expectationFiles: List<Path> =
        expectationsDir.walk().filter { it.name.endsWith(".expected.yaml") }.sorted().toList()

    private val regenMode: Boolean = System.getenv("HQL_REGEN_EXPECTED") == "1"

    // Block scalars (`|-`) for multi-line expected outputs — kaml needs both the
    // global multiLineStringStyle and the field-level annotation to honor the style
    // for short multiline strings (otherwise it falls back to single-line "...\n..." form).
    private val yaml: Yaml = Yaml(
        configuration = YamlConfiguration(
            multiLineStringStyle = MultiLineStringStyle.Literal
        )
    )

    @Test
    fun runAllQueries() {
        check(expectationFiles.isNotEmpty()) {
            "no *.expected.yaml files found under $expectationsDir"
        }

        val report = StringBuilder()
        var failures = 0

        for (yamlPath in expectationFiles) {
            val hprofName = yamlPath.name.removeSuffix(".expected.yaml") + ".hprof"
            val hprofPath = dumpsDir / hprofName
            check(hprofPath.exists()) { "missing dump $hprofName for $yamlPath" }

            val yamlText = yamlPath.readText()
            val expectations = yaml.decodeFromString(QueryExpectations.serializer(), yamlText)

            val database = hprofPath.inputStream().use { stream ->
                Database(Heap(HprofReader(stream).getHprof()))
            }

            val regenerated = mutableListOf<QueryCase>()
            for ((idx, case) in expectations.queries.withIndex()) {
                val actual = captureStdout { database.query(case.query) }.trimEnd('\n')
                if (regenMode) {
                    regenerated.add(QueryCase(case.query, actual))
                    continue
                }
                val expected = case.expected.trimEnd('\n')
                if (actual != expected) {
                    failures++
                    report.appendLine("FAIL: $hprofName  query #${idx + 1}: ${case.query}")
                    report.appendLine("--- expected ---")
                    report.appendLine(expected)
                    report.appendLine("--- actual ---")
                    report.appendLine(actual)
                    report.appendLine()
                }
            }

            if (regenMode) {
                val newYaml = yaml.encodeToString(
                    QueryExpectations.serializer(),
                    QueryExpectations(regenerated)
                )
                yamlPath.writeText(newYaml)
            }
        }

        if (failures > 0) {
            fail("$failures query mismatch(es):\n$report")
        }
    }

    private fun captureStdout(block: () -> Unit): String {
        val original = System.out
        val buffer = ByteArrayOutputStream()
        System.setOut(PrintStream(buffer, true, StandardCharsets.UTF_8))
        try {
            block()
        } finally {
            System.setOut(original)
        }
        return buffer.toString(StandardCharsets.UTF_8)
    }
}
