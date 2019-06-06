package cfig.dtb_util

import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.slf4j.LoggerFactory

class DTC {
    private val log = LoggerFactory.getLogger(DTC::class.java)

    fun decompile(dtbFile: String, outFile: String): Boolean {
        log.info("parsing DTB: $dtbFile")
        val cmd = CommandLine.parse("dtc -I dtb -O dts").let {
            it.addArguments("$dtbFile")
            it.addArguments("-o $outFile")
        }

        CommandLine.parse("fdtdump").let {
            it.addArguments("$dtbFile")
        }

        DefaultExecutor().let {
            try {
                it.execute(cmd)
                log.info(cmd.toString())
            } catch (e: org.apache.commons.exec.ExecuteException) {
                log.error("can not parse DTB: $dtbFile")
                return false
            }
        }
        return true
    }

    fun compile(dtsFile: String, outFile: String): Boolean {
        log.info("compiling DTS: $dtsFile")
        val cmd = CommandLine.parse("dtc -I dts -O dtb").let {
            it.addArguments("$dtsFile")
            it.addArguments("-o $outFile")
        }

        DefaultExecutor().let {
            try {
                it.execute(cmd)
                log.info(cmd.toString())
            } catch (e: org.apache.commons.exec.ExecuteException) {
                log.error("can not compile DTB: $dtsFile")
                return false
            }
        }
        return true
    }
}
