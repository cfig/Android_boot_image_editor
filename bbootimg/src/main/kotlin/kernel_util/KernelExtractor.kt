package cfig.kernel_util

import cfig.InfoTable
import de.vandermeer.asciitable.AsciiTable
import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

class KernelExtractor {
    val log: Logger = LoggerFactory.getLogger("KernelExtractor")

    fun envCheck(): Boolean {
        try {
            Runtime.getRuntime().exec("lz4 --version")
            log.debug("lz4 available")
        } catch (e: Exception) {
            log.warn("lz4 unavailable")
            return false
        }

        try {
            Runtime.getRuntime().exec("xz --version")
            log.debug("xz available")
        } catch (e: Exception) {
            log.warn("xz unavailable")
            return false
        }

        try {
            Runtime.getRuntime().exec("gzip -V")
            log.debug("gzip available")
        } catch (e: Exception) {
            log.warn("gzip unavailable")
            return false
        }

        return true
    }

    fun run(fileName: String, workDir: File? = null) {
        val baseDir = "build/unzip_boot"
        val kernelVersionFile = "$baseDir/kernel_version.txt"
        val kernelConfigFile = "$baseDir/kernel_configs.txt"
        val cmd = CommandLine.parse("external/extract_kernel.py").let {
            it.addArgument("--input")
            it.addArgument(fileName)
            it.addArgument("--output-configs")
            it.addArgument(kernelConfigFile)
            it.addArgument("--output-version")
            it.addArgument(kernelVersionFile)
        }
        DefaultExecutor().let {
            it.workingDirectory = workDir ?: File("../")
            try {
                it.execute(cmd)
                log.info(cmd.toString())
                val kernelVersion = File(kernelVersionFile).readLines()
                log.info("kernel version: " + kernelVersion)
                log.info("kernel config dumped to : $kernelConfigFile")
                InfoTable.instance.addRow("\\-- version $kernelVersion", kernelVersionFile)
                InfoTable.instance.addRow("\\-- config", kernelConfigFile)
            } catch (e: org.apache.commons.exec.ExecuteException) {
                log.warn("can not parse kernel info")
            }
        }
    }
}
