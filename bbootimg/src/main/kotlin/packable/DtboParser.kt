// Copyright 2021 yuyezhong@gmail.com
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package cfig.packable

import avb.blob.Footer
import cfig.EnvironmentVerifier
import cfig.dtb_util.DTC
import cfig.helper.Helper
import cfig.Avb
import com.fasterxml.jackson.databind.ObjectMapper
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

        val props = Properties().apply {
            FileInputStream(File(headerPath)).use { fis ->
                load(fis)
            }
        }
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
        val props = Properties().apply {
            FileInputStream(File(headerPath)).use { fis ->
                load(fis)
            }
        }
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

    override fun `@verify`(fileName: String) {
        super.`@verify`(fileName)
    }

    // invoked solely by reflection
    fun `@footer`(fileName: String) {
        FileInputStream(fileName).use { fis ->
            fis.skip(File(fileName).length() - Footer.SIZE)
            try {
                val footer = Footer(fis)
                log.info("\n" + ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(footer))
            } catch (e: IllegalArgumentException) {
                log.info("image $fileName has no AVB Footer")
            }
        }
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
