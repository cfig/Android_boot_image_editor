package cfig.lazybox

import org.slf4j.LoggerFactory
import java.io.File

object BootTimeABCompare {
    private val log = LoggerFactory.getLogger(BootTimeABCompare::class.java)
    private val nameSanitizer = Regex("[^A-Za-z_]")

    fun run(args: Array<String>) {
        if (args.isEmpty() || args.contains("--help") || args.contains("-h")) {
            printUsage()
            return
        }

        val positionals = mutableListOf<String>()
        val milestones = mutableListOf<String>()
        var aName: String? = null
        var bName: String? = null
        var outDir: String? = null
        var reportName: String? = null

        var i = 0
        while (i < args.size) {
            val arg = args[i]
            if (!arg.startsWith("--")) {
                positionals.add(arg)
                i += 1
                continue
            }

            fun requireValue(): String {
                if (i + 1 >= args.size) {
                    throw IllegalArgumentException("Missing value for $arg")
                }
                return args[i + 1]
            }

            when (arg) {
                "--a-name" -> {
                    aName = requireValue()
                    i += 2
                }
                "--b-name" -> {
                    bName = requireValue()
                    i += 2
                }
                "--out-dir" -> {
                    outDir = requireValue()
                    i += 2
                }
                "--report-name" -> {
                    reportName = requireValue()
                    i += 2
                }
                "--milestone" -> {
                    milestones.add(requireValue())
                    i += 2
                }
                else -> {
                    throw IllegalArgumentException("Unknown option: $arg")
                }
            }
        }

        if (positionals.size < 2) {
            log.error("Missing required directories for A and B.")
            printUsage()
            return
        }

        val aDir = File(positionals[0]).absoluteFile
        val bDir = File(positionals[1]).absoluteFile
        if (!aDir.exists() || !aDir.isDirectory) {
            log.error("Directory not found: ${aDir.absolutePath}")
            return
        }
        if (!bDir.exists() || !bDir.isDirectory) {
            log.error("Directory not found: ${bDir.absolutePath}")
            return
        }

        val script = findScript(File(".").absoluteFile)
        if (script == null) {
            log.error("boot_analysis.py not found under current or parent directories")
            return
        }

        val aLabel = aName ?: aDir.name
        val bLabel = bName ?: bDir.name
        val defaultOutDir = "${sanitizeName(aLabel)}_VS_${sanitizeName(bLabel)}"

        val cmd = mutableListOf(
            "python3",
            script.absolutePath,
            "--a-name",
            aLabel,
            "--a-dir",
            aDir.absolutePath,
            "--b-name",
            bLabel,
            "--b-dir",
            bDir.absolutePath,
        )
        cmd.addAll(listOf("--out-dir", outDir ?: defaultOutDir))
        if (reportName != null) {
            cmd.addAll(listOf("--report-name", reportName!!))
        }
        if (milestones.isNotEmpty()) {
            milestones.forEach { milestone ->
                cmd.addAll(listOf("--milestone", milestone))
            }
        }

        log.info("Running boot_analysis.py for A=$aLabel B=$bLabel")
        val process = ProcessBuilder(cmd)
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()

        val exit = process.waitFor()
        if (exit != 0) {
            log.error("boot_analysis.py failed with exit code: $exit")
        }
    }

    private fun printUsage() {
        println("Usage: stat AB <a_dir> <b_dir> [--a-name NAME] [--b-name NAME]")
        println("           [--out-dir DIR] [--report-name NAME] [--milestone NAME]...")
    }

    private fun findScript(startDir: File): File? {
        var dir: File? = startDir
        while (dir != null) {
            val candidate = File(dir, "boot_analysis.py")
            if (candidate.exists()) {
                return candidate
            }
            dir = dir.parentFile
        }
        return null
    }

    private fun sanitizeName(name: String): String {
        return nameSanitizer.replace(name, "_")
    }
}
