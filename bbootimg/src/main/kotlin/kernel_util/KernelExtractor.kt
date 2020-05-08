package cfig.kernel_util

import cfig.EnvironmentVerifier
import cfig.Helper
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

    fun run(fileName: String, workDir: File? = null): List<String> {
        val ret: MutableList<String> = mutableListOf()
        val kernelVersionFile = Helper.prop("kernelVersionFile")
        val kernelConfigFile = Helper.prop("kernelConfigFile")
        val cmd = CommandLine.parse(Helper.prop("kernelExtracter")).let {
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
                ret.add(kernelVersion.toString())
                ret.add(kernelVersionFile)
                ret.add(kernelConfigFile)
            } catch (e: org.apache.commons.exec.ExecuteException) {
                log.warn("can not parse kernel info")
            }
        }
        return ret
    }
}
