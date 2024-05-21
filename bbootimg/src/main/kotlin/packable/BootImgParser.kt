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
import cfig.bootimg.Common.Companion.probeHeaderVersion
import cfig.bootimg.v2.BootV2
import cfig.bootimg.v2.BootV2Dialects
import cfig.bootimg.v3.BootV3
import cfig.helper.Helper
import com.fasterxml.jackson.databind.ObjectMapper
import de.vandermeer.asciitable.AsciiTable
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import kotlin.io.path.Path
import kotlin.io.path.deleteIfExists
import kotlin.system.exitProcess

class BootImgParser : IPackable {
    override val loopNo: Int
        get() = 0

    override fun capabilities(): List<String> {
        //ramdisk.img : Issue #122
        return listOf(
            "^boot(-debug)?\\.img$",
            "^recovery\\.img$",
            "^recovery-two-step\\.img$",
            "^init_boot\\.img$",
            "^ramdisk\\.img$"
        )
    }

    override fun unpack(fileName: String) {
        unpackInternal(fileName, fileName, outDir)
    }

    fun unpackInternal(targetFile: String, fileName: String, unpackDir: String) {
        log.info("unpackInternal(fileName: $fileName, unpackDir: $unpackDir)")
        Helper.setProp("workDir", unpackDir)
        clear()
        File("$outDir/role").writeText(File(File(targetFile).canonicalPath).name)
        val hv = probeHeaderVersion(fileName)
        log.info("header version $hv")
        when (hv) {
            in 0..2 -> {
                val b2 = BootV2
                    .parse(fileName)
                    .extractImages()
                    .extractVBMeta()
                    .printUnpackSummary()
                log.debug(b2.toString())
            }

            in 3..4 -> {
                val b3 = BootV3
                    .parse(fileName)
                    .extractImages()
                    .extractVBMeta()
                    .printUnpackSummary()
                log.debug(b3.toString())
            }

            else -> {
                val b2 = BootV2Dialects
                    .parse(fileName)
                    .extractImages()
                    .extractVBMeta()
                    .printSummary()
                log.debug(b2.toString())
            }
        }
    }

    fun packInternal(targetFile: String, workspace: String, fileName: String) {
        log.info("packInternal(targetFile: $targetFile, fileName: $fileName, workspace: $workspace)")
        Helper.setProp("workDir", workspace)
        val cfgFile = Helper.joinPath(outDir, targetFile.removeSuffix(".img") + ".json")
        log.info("Loading config from $cfgFile")
        if (!File(cfgFile).exists()) {
            val tab = AsciiTable().let {
                it.addRule()
                it.addRow("'$cfgFile' doesn't exist, did you forget to 'unpack' ?")
                it.addRule()
                it
            }
            log.info("\n{}", tab.render())
            return
        }

        val worker =
            try {
                ObjectMapper().readValue(File(cfgFile), BootV2::class.java)
            } catch (e: com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException) {
                try {
                    ObjectMapper().readValue(File(cfgFile), BootV3::class.java)
                } catch (e: com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException) {
                    null
                }
            }
        if (worker == null) {
            log.error("no worker available")
            exitProcess(2)
        }
        when (worker) {
            is BootV2 -> {
                worker
                    .pack()
                    .sign()
                    .updateVbmeta()
                    .printPackSummary()
            }

            is BootV3 -> {
                worker
                    .pack()
                    .sign(fileName)
                    .updateVbmeta()
                    .printPackSummary(worker.info.role)
            }

            else -> {
                log.error("unsupported boot image format")
                exitProcess(2)
            }
        }
    }

    override fun pack(fileName: String) {
        packInternal(fileName, outDir, fileName)
    }

    fun flash(fileName: String) {
        val stem = fileName.substring(0, fileName.indexOf("."))
        super.flash("$fileName.signed", stem)

        if (File("vbmeta.img.signed").exists()) {
            super.flash("vbmeta.img.signed", "vbmeta")
        }
    }

    // invoked solely by reflection
    fun `@footer`(image_file: String) {
        FileInputStream(image_file).use { fis ->
            fis.skip(File(image_file).length() - Footer.SIZE)
            try {
                val footer = Footer(fis)
                log.info("\n" + ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(footer))
            } catch (e: IllegalArgumentException) {
                log.info("image $image_file has no AVB Footer")
            }
        }
    }

    override fun `@verify`(fileName: String) {
        File(Helper.prop("workDir")!!).let {
            if (!it.exists()) {
                it.mkdirs()
            }
        }

        super.`@verify`(fileName)
    }

    fun pull(fileName: String) {
        super.pull(fileName, File(fileName).nameWithoutExtension)
        try {
            super.pull("vbmeta.img", "vbmeta")
        } catch (e: Exception) {
            log.warn("can not pull vbmeta.img")
        }
    }

    fun clear(fileName: String) {
        super.clear()
        listOf("", ".clear", ".google", ".clear", ".signed", ".signed2").forEach {
            Path("$fileName$it").deleteIfExists()
        }
        VBMetaParser().clear("vbmeta.img")
    }

    companion object {
        private val log = LoggerFactory.getLogger(BootImgParser::class.java)
    }
}
