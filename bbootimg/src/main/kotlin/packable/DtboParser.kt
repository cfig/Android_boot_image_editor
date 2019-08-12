package cfig.packable

import cfig.EnvironmentVerifier
import cfig.UnifiedConfig
import cfig.dtb_util.DTC
import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.util.*

@ExperimentalUnsignedTypes
class DtboParser(val workDir: File) : IPackable {
    override fun flash(fileName: String) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    constructor() : this(File("."))

    private val log = LoggerFactory.getLogger(DtboParser::class.java)
    private val envv = EnvironmentVerifier()

    override fun capabilities(): List<String> {
        return listOf("^dtbo\\.img$")
    }

    override fun unpack(fileName: String) {
        val outputDir = UnifiedConfig.workDir
        val dtbPath = File("$outputDir/dtb").path!!
        val headerPath = File("$outputDir/dtbo.header").path!!
        val cmd = CommandLine.parse("external/mkdtboimg.py dump $fileName").let {
            it.addArguments("--dtb $dtbPath")
            it.addArguments("--output $headerPath")
        }

        DefaultExecutor().let {
            it.workingDirectory = this.workDir
            try {
                log.info(cmd.toString())
                it.execute(cmd)
            } catch (e: org.apache.commons.exec.ExecuteException) {
                log.error("can not parse $fileName")
                return
            }
        }

        val props = Properties()
        props.load(FileInputStream(File(headerPath)))
        if (envv.hasDtc) {
            for (i in 0 until Integer.parseInt(props.getProperty("dt_entry_count"))) {
                val inputDtb = "$dtbPath.$i"
                val outputSrc = File(UnifiedConfig.workDir + "/" + File(inputDtb).name + ".src").path
                DTC().decompile(inputDtb, outputSrc)
            }
        } else {
            log.error("'dtc' is unavailable, task aborted")
        }
    }

    override fun pack(fileName: String) {
        if (!envv.hasDtc) {
            log.error("'dtc' is unavailable, task aborted")
            return
        }

        val headerPath = File("${UnifiedConfig.workDir}/dtbo.header").path
        val props = Properties()
        props.load(FileInputStream(File(headerPath)))
        val cmd = CommandLine.parse("external/mkdtboimg.py create $fileName.clear").let {
            it.addArguments("--version=1")
            for (i in 0 until Integer.parseInt(props.getProperty("dt_entry_count"))) {
                val dtsName = File(UnifiedConfig.workDir + "/dtb.$i").path
                it.addArguments(dtsName)
            }
            it
        }

        DefaultExecutor().let {
            it.workingDirectory = this.workDir
            try {
                log.info(cmd.toString())
                it.execute(cmd)
            } catch (e: org.apache.commons.exec.ExecuteException) {
                log.error("can not parse $fileName")
                return
            }
        }
    }
}
