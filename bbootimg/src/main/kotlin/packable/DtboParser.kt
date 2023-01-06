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

import cfig.helper.Helper
import cfig.helper.Helper.Companion.deleteIfExists
import cfig.utils.DTC
import cfig.utils.EnvironmentVerifier
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.slf4j.LoggerFactory
import utils.Dtbo
import java.io.File
import java.io.FileInputStream
import java.util.*

class DtboParser(val workDir: File) : IPackable {
    override val loopNo: Int
        get() = 0

    constructor() : this(File("."))

    private val log = LoggerFactory.getLogger(DtboParser::class.java)
    private val envv = EnvironmentVerifier()
    private val dtboMaker = Helper.prop("dtboMaker")
    private val dtsSuffix = Helper.prop("config.dts_suffix")

    override fun capabilities(): List<String> {
        return listOf("^dtbo\\.img$")
    }

    override fun unpack(fileName: String) {
        clear()
        Dtbo.parse(fileName)
            .unpack(outDir)
            .extractVBMeta()
            .printSummary()
    }

    override fun pack(fileName: String) {
        ObjectMapper().readValue(File(outDir + "dtbo.json"), Dtbo::class.java)
            .pack()
            .sign()
            .updateVbmeta()
            .printPackSummary()
    }

    override fun pull(fileName: String, deviceName: String) {
        super.pull(fileName, deviceName)
    }

    override fun flash(fileName: String, deviceName: String) {
        val stem = fileName.substring(0, fileName.indexOf("."))
        super.flash("$fileName.signed", stem)

        if (File("vbmeta.img.signed").exists()) {
            super.flash("vbmeta.img.signed", "vbmeta")
        }
    }

    override fun `@verify`(fileName: String) {
        super.`@verify`(fileName)
    }

    fun clear(fileName: String) {
        super.clear()
        listOf("", ".clear", ".google", ".clear", ".signed", ".signed2").forEach {
            "$fileName$it".deleteIfExists()
        }
        VBMetaParser().clear("vbmeta.img")
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

    @Deprecated("for debugging purpose only")
    fun packLegacy(fileName: String) {
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

    @Deprecated("for debugging purpose only")
    fun unpackLegacy(fileName: String) {
        clear()
        val dtbPath = File("$outDir/dtb").path
        val headerPath = File("$outDir/dtbo.header").path
        val cmdPrefix = if (EnvironmentVerifier().isWindows) "python " else ""
        val cmd = CommandLine.parse("$cmdPrefix$dtboMaker dump $fileName").let {
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
                val outputSrc = File(outDir + "/" + File(inputDtb).name + ".${dtsSuffix}").path
                DTC().decompile(inputDtb, outputSrc)
            }
        } else {
            log.error("'dtc' is unavailable, task aborted")
        }
    }
}
