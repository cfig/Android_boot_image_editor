package cfig.packable

import cfig.EnvironmentVerifier
import cfig.Helper
import cfig.dtb_util.DTC
import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.util.*

@OptIn(ExperimentalUnsignedTypes::class)
class DtboParser(val workDir: File) : IPackable {
    override val loopNo: Int
        get() = 0

    constructor() : this(File("."))

    private val log = LoggerFactory.getLogger(DtboParser::class.java)
    private val envv = EnvironmentVerifier()
    private val outDir = Helper.prop("workDir")
    private val dtboMaker = Helper.prop("dtboMaker")

    override fun capabilities(): List<String> {
        return listOf("^dtbo\\.img$")
    }

    override fun unpack(fileName: String) {
        cleanUp()
        val dtbPath = File("$outDir/dtb").path
        val headerPath = File("$outDir/dtbo.header").path
        val cmd = CommandLine.parse("$dtboMaker dump $fileName").let {
            it.addArguments("--dtb $dtbPath")
            it.addArguments("--output $headerPath")
        }
        execInDirectory(cmd, this.workDir)

        val props = Properties()
        props.load(FileInputStream(File(headerPath)))
        if (envv.hasDtc) {
            for (i in 0 until Integer.parseInt(props.getProperty("dt_entry_count"))) {
                val inputDtb = "$dtbPath.$i"
                val outputSrc = File(outDir + "/" + File(inputDtb).name + ".src").path
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

        val headerPath = File("${outDir}/dtbo.header").path
        val props = Properties()
        props.load(FileInputStream(File(headerPath)))
        val cmd = CommandLine.parse("$dtboMaker create $fileName.clear").let {
            it.addArguments("--version=1")
            for (i in 0 until Integer.parseInt(props.getProperty("dt_entry_count"))) {
                val dtsName = File("$outDir/dtb.$i").path
                it.addArguments(dtsName)
            }
            it
        }
        execInDirectory(cmd, this.workDir)
    }

    private fun execInDirectory(cmd: CommandLine, inWorkDir: File) {
        DefaultExecutor().let {
            it.workingDirectory = inWorkDir
            try {
                log.info(cmd.toString())
                it.execute(cmd)
            } catch (e: org.apache.commons.exec.ExecuteException) {
                log.error("can not exec command")
                return
            }
        }
    }
}
