package cfig.kernel_util

import cfig.EnvironmentVerifier
import cfig.InfoTable
import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

class KernelExtractor {
    val log: Logger = LoggerFactory.getLogger("KernelExtractor")

    fun envCheck(): Boolean {
        val envv = EnvironmentVerifier()
        return envv.hasLz4 && envv.hasXz && envv.hasGzip
    }

    fun run(fileName: String, workDir: File? = null) {
        val baseDir = "build/unzip_boot"
        val kernelVersionFile = "$baseDir/kernel_version.txt"
        val kernelConfigFile = "$baseDir/kernel_configs.txt"
        val cmd = CommandLine.parse("aosp/build/tools/extract_kernel.py").let {
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
                log.info("kernel version: $kernelVersion")
                log.info("kernel config dumped to : $kernelConfigFile")
                InfoTable.instance.addRow("\\-- version $kernelVersion", kernelVersionFile)
                InfoTable.instance.addRow("\\-- config", kernelConfigFile)
            } catch (e: org.apache.commons.exec.ExecuteException) {
                log.warn("can not parse kernel info")
            }
        }
    }
}
