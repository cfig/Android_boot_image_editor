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

package cfig.utils

import avb.blob.Footer
import cc.cfig.io.Struct
import cfig.bootimg.Common.Companion.deleleIfExists
import cfig.helper.Helper
import cfig.helper.Helper.Companion.check_call
import cfig.helper.Helper.Companion.check_output
import cfig.packable.IPackable
import com.fasterxml.jackson.databind.ObjectMapper
import de.vandermeer.asciitable.AsciiTable
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.util.*

class SparseImgParser : IPackable {
    override val loopNo: Int
        get() = 0
    private val log = LoggerFactory.getLogger(SparseImgParser::class.java)
    private val simg2imgBin: String
    private val img2simgBin: String

    init {
        val osSuffix = if (EnvironmentVerifier().isMacOS) "macos" else "linux"
        simg2imgBin = "./aosp/libsparse/simg2img/build/install/main/release/$osSuffix/simg2img"
        img2simgBin = "./aosp/libsparse/img2simg/build/install/main/release/$osSuffix/img2simg"
    }

    override fun capabilities(): List<String> {
        return listOf(
            "^(system|system_ext|system_other|system_dlkm)\\.img$",
            "^(vendor|vendor_dlkm|product|cache|userdata|super|oem|odm|odm_dlkm)\\.img$"
        )
    }

    override fun unpack(fileName: String) {
        clear()
        var target = fileName
        if (isSparse(fileName)) {
            val tempFile = UUID.randomUUID().toString()
            outerFsType = "sparse"
            val rawFile = "$workDir${File(fileName).nameWithoutExtension}"
            simg2img(fileName, tempFile)
            target = if (isExt4(tempFile)) {
                innerFsType = "ext4"
                "$rawFile.ext4"
            } else if (isErofs(tempFile)) {
                innerFsType = "erofs"
                "$rawFile.erofs"
            } else {
                "$rawFile.raw"
            }
            File(tempFile).renameTo(File(target))
        } else if (isExt4(fileName)) {
            outerFsType = "ext4"
            innerFsType = "ext4"
        } else if (isErofs(fileName)) {
            outerFsType = "erofs"
            innerFsType = "erofs"
        }
        when (innerFsType) {
            "ext4" -> {
                extractExt4(target)
            }

            "erofs" -> {
                extraceErofs(target)
            }

            else -> {
                log.warn("unsuported image type: $innerFsType")
            }
        }
        File("${workDir}mount").mkdir()
        printSummary(fileName)
    }

    override fun pack(fileName: String) {
        TODO("not implemented")
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

    override fun `@verify`(fileName: String) {
        super.`@verify`(fileName)
    }

    private fun simg2img(sparseIn: String, flatOut: String) {
        log.info("parsing Android sparse image $sparseIn ...")
        "$simg2imgBin $sparseIn $flatOut".check_call()
        log.info("parsed Android sparse image $sparseIn -> $flatOut")
    }

    private fun img2simg(flatIn: String, sparseOut: String) {
        log.info("transforming image to Android sparse format: $flatIn ...")
        "$img2simgBin $flatIn $sparseOut".check_call()
        log.info("transformed Android sparse image: $flatIn -> $sparseOut")
    }

    override fun flash(fileName: String, deviceName: String) {
        TODO("not implemented")
    }

    fun clear(fileName: String) {
        super.clear()
        File(fileName).deleleIfExists()
    }

    private fun isSparse(fileName: String): Boolean {
        val magic = Helper.Companion.readFully(fileName, 0, 4)
        return Struct(">I").pack(SPARSE_MAGIC).contentEquals(magic)
    }

    private fun isExt4(fileName: String): Boolean {
        val superBlock = Helper.readFully(fileName, 1024, 64)
        val magic = byteArrayOf(superBlock[0x38], superBlock[0x39])
        return Struct(">h").pack(0x53ef).contentEquals(magic)
    }

    // https://elixir.bootlin.com/linux/latest/source/include/uapi/linux/magic.h#L23
    private fun isErofs(fileName: String): Boolean {
        val magic = Helper.readFully(fileName, 1024, 4)
        return Struct(">I").pack(0xe2e1f5e0).contentEquals(magic)
    }

    private fun extractExt4(fileName: String) {
        if (EnvironmentVerifier().has7z) {
            val stem = File(fileName).nameWithoutExtension
            val outStr = "7z x $fileName -y -o$workDir$stem".check_output()
            File("$workDir/$stem.log").writeText(outStr)
        } else {
            log.warn("Please install 7z for ext4 extraction")
        }
    }

    private fun extraceErofs(fileName: String) {
        log.info("sudo mount $fileName -o loop -t erofs ${workDir}mount")
    }

    private fun printSummary(fileName: String) {
        val stem = File(fileName).nameWithoutExtension
        val tail = AsciiTable().apply {
            addRule()
            addRow("To view erofs contents:")
        }
        val tab = AsciiTable().apply {
            addRule()
            addRow("What", "Where")
            addRule()
            addRow("image ($outerFsType)", fileName)
            ("$workDir$stem.ext4").let { ext4 ->
                if (File(ext4).exists()) {
                    addRule()
                    addRow("converted image (ext4)", ext4)
                }
            }
            ("$workDir$stem.erofs").let {
                if (File(it).exists()) {
                    addRule()
                    addRow("converted image (erofs)", it)
                    tail.addRule()
                    tail.addRow("sudo mount $it -o loop -t erofs ${workDir}mount")
                    tail.addRule()
                } else if (innerFsType == "erofs") {
                    tail.addRule()
                    tail.addRow("sudo mount $fileName -o loop -t erofs ${workDir}mount")
                    tail.addRule()
                }
            }
            ("$workDir$stem").let {
                if (File(it).exists()) {
                    addRule()
                    if (File(it).isFile) {
                        addRow("converted image (raw)", it)
                    } else {
                        addRow("extracted content", it)
                    }
                }
            }
            ("$workDir$stem.log").let {
                if (File(it).exists()) {
                    addRule()
                    addRow("log", it)
                }
            }
            if (innerFsType == "erofs") {
                addRule()
                addRow("mount point", "${workDir}mount")
            }
            addRule()
        }
        log.info("\n" + tab.render() + "\n" + if (innerFsType == "erofs") tail.render() else "")
    }

    companion object {
        private val SPARSE_MAGIC: UInt = 0x3aff26edu
        private val workDir = Helper.prop("workDir")
        private var outerFsType = "raw"
        private var innerFsType = "raw"
    }
}
