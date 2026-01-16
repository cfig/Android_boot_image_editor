package cfig.lazybox

import java.io.File
import kotlin.math.sqrt
import kotlin.system.exitProcess

data class BootRun(val name: String, val stages: Map<String, Double>)

data class StageStats(
    val mean: Double,
    val std: Double,
    val min: Double,
    val max: Double,
    val jitter: Double,
)

data class Outlier(val run: String, val stage: String, val value: Double, val median: Double, val threshold: Double)

object BootTimeStability {
    private val requiredStages = listOf(
        "Kernel Start",
        "Boot Progress Start",
        "AMS Ready",
        "Boot Completed",
        "Launcher Start",
        "Launcher loaded",
    )

    private fun parseSeconds(token: String): Double? {
        val t = token.trim().removeSuffix("s").trim()
        return t.toDoubleOrNull()
    }

    private fun parseMergedBootReport(file: File): Map<String, Double> {
        val found = mutableMapOf<String, Double>()

        file.forEachLine { line ->
            val trimmed = line.trim()
            if (!trimmed.startsWith("|")) return@forEachLine
            if (trimmed.startsWith("|---") || trimmed.contains("TimeStamp") && trimmed.contains("Delta(A)")) return@forEachLine

            val cols = trimmed
                .split("|")
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            if (cols.size < 6) return@forEachLine

            val logEvent = cols[1]
            val visualEvent = cols[2]
            val deltaA = parseSeconds(cols[3]) ?: return@forEachLine

            val logEventLc = logEvent.lowercase()
            val visualEventLc = visualEvent.lowercase()

            fun maybeSet(stage: String, matched: Boolean) {
                if (matched && !found.containsKey(stage)) {
                    found[stage] = deltaA
                }
            }

            maybeSet("Kernel Start", logEventLc == "kernel start" || visualEventLc == "kernel start")
            maybeSet("Boot Progress Start", logEventLc == "boot progress start" || visualEventLc == "boot progress start")
            maybeSet("AMS Ready", logEventLc == "ams ready" || visualEventLc == "ams ready")
            maybeSet("Boot Completed", logEventLc == "boot completed" || visualEventLc == "boot completed")
            maybeSet("Launcher Start", logEventLc == "launcher start" || visualEventLc == "launcher start")
            maybeSet("Launcher loaded", logEventLc == "launcher loaded" || visualEventLc == "launcher loaded")
        }

        return found
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val n = sorted.size
        return if (n % 2 == 1) {
            sorted[n / 2]
        } else {
            (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0
        }
    }

    private fun mean(values: List<Double>): Double = if (values.isEmpty()) 0.0 else values.sum() / values.size

    private fun sampleStd(values: List<Double>): Double {
        if (values.size <= 1) return 0.0
        val m = mean(values)
        val variance = values.sumOf { (it - m) * (it - m) } / (values.size - 1)
        return sqrt(variance)
    }

    private fun statsForStage(runs: List<BootRun>, stage: String): StageStats {
        val values = runs.mapNotNull { it.stages[stage] }
        if (values.isEmpty()) return StageStats(0.0, 0.0, 0.0, 0.0, 0.0)
        val minV = values.minOrNull() ?: 0.0
        val maxV = values.maxOrNull() ?: 0.0
        return StageStats(
            mean = mean(values),
            std = sampleStd(values),
            min = minV,
            max = maxV,
            jitter = maxV - minV,
        )
    }

    private fun format3(v: Double): String = String.format("%.3f", v)

    private fun markdownTable(runs: List<BootRun>): String {
        val header = buildString {
            append("| Run | ")
            append(requiredStages.joinToString(" | "))
            append(" |\n")
            append("|:--|")
            append(requiredStages.joinToString("|") { "--:" })
            append("|\n")
        }

        val rows = runs.joinToString("\n") { run ->
            val cols = requiredStages.joinToString(" | ") { stage ->
                val v = run.stages[stage]
                if (v == null) "" else format3(v)
            }
            "| ${run.name} | $cols |"
        }

        return header + rows + "\n"
    }

    private fun markdownStatsTable(stats: Map<String, StageStats>): String {
        fun s(stage: String) = stats[stage] ?: StageStats(0.0, 0.0, 0.0, 0.0, 0.0)

        val header = "| Metric         | ${requiredStages.joinToString(" | ")} |"
        val sep = "|----------------|" + requiredStages.joinToString("|") { "------:" } + "|"

        val lineMean = "| **Mean**       | " + requiredStages.joinToString(" | ") { stage -> format3(s(stage).mean) } + " |"
        val lineStd = "| **Std Dev**    | " + requiredStages.joinToString(" | ") { stage -> format3(s(stage).std) } + " |"
        val lineJitter = "| **Jitter**     | " + requiredStages.joinToString(" | ") { stage -> format3(s(stage).jitter) } + " |"

        return listOf(header, sep, lineMean, lineStd, lineJitter).joinToString("\n") + "\n"
    }

    fun run(args: Array<String>) {
        if (args.isEmpty()) {
            System.err.println("Usage: lazybox stat outlier <directory>")
            exitProcess(1)
        }

        val inputDir = File(args[0])
        if (!inputDir.exists() || !inputDir.isDirectory) {
            System.err.println("Error: '${inputDir.absolutePath}' is not a directory")
            exitProcess(1)
        }

        val runDirs = inputDir.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name } ?: emptyList()

        println("[boot-stability] inputDir=${inputDir.absolutePath}")
        println("[boot-stability] found ${runDirs.size} first-level subdirectories")

        val parsedRuns = mutableListOf<BootRun>()
        val skippedRuns = mutableListOf<String>()

        for (dir in runDirs) {
            val reportFile = File(dir, "merged_boot_report.md")
            if (!reportFile.exists() || !reportFile.isFile) {
                skippedRuns.add("${dir.name} (missing merged_boot_report.md)")
                println("[boot-stability] SKIP ${dir.name}: merged_boot_report.md not found")
                continue
            }

            val stageMap = parseMergedBootReport(reportFile)

            val foundStages = requiredStages.filter { stageMap.containsKey(it) }
            val missingStages = requiredStages.filter { !stageMap.containsKey(it) }
            println(
                "[boot-stability] RUN ${dir.name}: found=${foundStages.joinToString(", ")}" +
                    if (missingStages.isEmpty()) "" else "; missing=${missingStages.joinToString(", ")}"
            )
            parsedRuns.add(BootRun(dir.name, stageMap))
        }

        if (parsedRuns.isEmpty()) {
            System.err.println("No valid runs found under: ${inputDir.absolutePath}")
            exitProcess(2)
        }

        val originalRuns = parsedRuns.toList()

        println("[boot-stability] valid runs=${originalRuns.size}, skipped=${skippedRuns.size}")

        val mediansByStage = requiredStages.associateWith { stage ->
            median(originalRuns.mapNotNull { it.stages[stage] })
        }

        for (stage in requiredStages) {
            val m = mediansByStage[stage] ?: 0.0
            if (m > 0.0) {
                println("[boot-stability] median[$stage]=${format3(m)} threshold=${format3(m * 2.0)}")
            } else {
                println("[boot-stability] median[$stage]=N/A (no samples)")
            }
        }

        val outliers = mutableListOf<Outlier>()
        val outlierRunNames = mutableSetOf<String>()

        for (stage in requiredStages) {
            val medianV = mediansByStage[stage] ?: 0.0
            val threshold = medianV * 2.0
            for (run in originalRuns) {
                val v = run.stages[stage] ?: continue
                if (medianV > 0.0 && v > threshold) {
                    outliers.add(Outlier(run.name, stage, v, medianV, threshold))
                    outlierRunNames.add(run.name)
                }
            }
        }

        if (outliers.isEmpty()) {
            println("[boot-stability] outliers: none")
        } else {
            println("[boot-stability] outliers: ${outliers.size}")
            outliers.forEach { o ->
                println(
                    "[boot-stability] outlier run=${o.run} stage=${o.stage} " +
                        "value=${format3(o.value)} threshold=${format3(o.threshold)}"
                )
            }
            println("[boot-stability] outlier runs=${outlierRunNames.sorted().joinToString(", ")}")
        }

        val cleanedRuns = originalRuns.filter { it.name !in outlierRunNames }

        println("[boot-stability] cleaned runs=${cleanedRuns.size}")

        if (outlierRunNames.isNotEmpty()) {
            println("[boot-stability] removed runs=${outlierRunNames.sorted().joinToString(", ")}")
        }

        val originalStats = requiredStages.associateWith { stage -> statsForStage(originalRuns, stage) }
        val cleanedStats = requiredStages.associateWith { stage -> statsForStage(cleanedRuns, stage) }

        println("[boot-stability] original stats summary:")
        for (stage in requiredStages) {
            val n = originalRuns.mapNotNull { it.stages[stage] }.size
            val st = originalStats.getValue(stage)
            if (n == 0) {
                println("[boot-stability]  - $stage: n=0")
            } else {
                println("[boot-stability]  - $stage: n=$n mean=${format3(st.mean)} std=${format3(st.std)} jitter=${format3(st.jitter)}")
            }
        }

        println("[boot-stability] cleaned stats summary:")
        for (stage in requiredStages) {
            val n = cleanedRuns.mapNotNull { it.stages[stage] }.size
            val st = cleanedStats.getValue(stage)
            if (n == 0) {
                println("[boot-stability]  - $stage: n=0")
            } else {
                println("[boot-stability]  - $stage: n=$n mean=${format3(st.mean)} std=${format3(st.std)} jitter=${format3(st.jitter)}")
            }
        }

        val outlierListStr = if (outliers.isEmpty()) {
            "No outliers detected."
        } else {
            outliers.joinToString("\n") { o ->
                "- Run `${o.run}` in stage **${o.stage}**: Value `${format3(o.value)}s` > Threshold `${format3(o.threshold)}s` (Median was ${format3(o.median)}s)"
            }
        }

        val outlierImpactSummary = when {
            outliers.isEmpty() -> "- **Outlier Impact**: No outliers were detected, indicating consistent boot times across all runs."
            outliers.size == 1 -> {
                val o = outliers[0]
                "- **Outlier Impact**: The run `${o.run}` was identified as an outlier. The most significant deviation was in the **${o.stage}** stage, " +
                    "with a time of `${format3(o.value)}s`, which is more than double the median of the stable runs (`${format3(o.median)}s`)."
            }
            else -> "- **Outlier Impact**: ${outliers.size} outliers were detected across various runs and stages. " +
                "Refer to the 'Data Cleaning: Outlier Detection' section above for detailed information on each outlier. " +
                "These outliers represent significant deviations from the median boot times in their respective stages."
        }

        val outlierRunForConclusion = outlierRunNames.firstOrNull() ?: "N/A"

        val skippedSection = if (skippedRuns.isEmpty()) {
            ""
        } else {
            """
## Skipped Runs

The following runs were skipped because they were missing required inputs:

${skippedRuns.joinToString("\n") { "- $it" }}

""".trimIndent() + "\n"
        }

        val report = """
# Boot Time Stability Analysis Report (kts)

This report analyzes the boot time of the device based on ${originalRuns.size} runs, with data cleaning applied to remove outliers.
A more robust outlier detection method is used: a data point is considered an outlier if its value is greater than 200% of the **median** for that specific boot stage (`value > 2 * median`).

## 1. Original Data Summary

### Boot Times (in seconds)
${markdownTable(originalRuns)}

### Statistical Analysis (Original Data, All Runs)
${markdownStatsTable(originalStats)}

## 2. Data Cleaning: Outlier Detection

The following outliers were detected and removed based on the median rule:

$outlierListStr

## 3. Cleaned Data Summary

### Statistical Analysis (Cleaned Data, ${cleanedRuns.size} Stable Runs)
_This analysis excludes the entire run(s) identified as containing outliers._

${markdownStatsTable(cleanedStats)}

## 4. Observations

$outlierImpactSummary
- **Stability (Cleaned Data)**: After removing the outlier run, the data for the remaining ${cleanedRuns.size} runs is more stable. For example, the standard deviation for 'Kernel Start' dropped from `${format3(originalStats.getValue("Kernel Start").std)}s` to `${format3(cleanedStats.getValue("Kernel Start").std)}s`.
- **Conclusion**: The cleaned data provides an accurate representation of typical boot performance. The outlier run from `$outlierRunForConclusion` should be investigated separately to understand the cause of its significant delay.

$skippedSection
""".trimIndent() + "\n"

        val outFile = File(inputDir, "boot_time_stability_report.md")
        outFile.writeText(report)

        println("Report generated successfully at ${outFile.absolutePath}")
    }
}
