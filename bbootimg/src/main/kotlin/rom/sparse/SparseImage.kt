package rom.sparse

import avb.AVBInfo
import avb.desc.HashTreeDescriptor
import cc.cfig.io.Struct
import cfig.Avb
import cfig.bootimg.Common.Companion.deleleIfExists
import cfig.helper.Dumpling
import cfig.helper.Helper
import cfig.helper.Helper.Companion.check_call
import cfig.helper.Helper.Companion.check_output
import cfig.packable.VBMetaParser
import cfig.utils.EnvironmentVerifier
import com.fasterxml.jackson.databind.ObjectMapper
import de.vandermeer.asciitable.AsciiTable
import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.apache.commons.exec.PumpStreamHandler
import org.apache.commons.exec.environment.EnvironmentUtils
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.util.*
import kotlin.io.path.Path

data class SparseImage(var info: SparseInfo = SparseInfo()) {
    fun pack(): SparseImage {
        val readBackAi = ObjectMapper().readValue(File(Avb.getJsonFileName(info.pulp)), AVBInfo::class.java)
        val partName = readBackAi.auxBlob!!.hashTreeDescriptors.get(0).partition_name
        when (info.innerFsType) {
            "ext4" -> {
                Ext4Generator(partName).pack(
                    readBackAi,
                    partName,
                    workDir,
                    workDir + File(info.output).nameWithoutExtension,
                    workDir + File(info.pulp).name + ".signed"
                )
            }

            "erofs" -> {
                ErofsGenerator(partName).pack(
                    readBackAi,
                    partName,
                    workDir,
                    workDir + File(info.output).nameWithoutExtension,
                    workDir + "${info.output}.signed"
                )
            }

            else -> {
                log.warn("unsuported image type: ${info.innerFsType}")
            }
        }
        return this
    }

    fun printSummary(fileName: String) {
        val stem = File(fileName).nameWithoutExtension
        val tail = AsciiTable().apply {
            addRule()
            addRow("To view erofs contents:")
        }
        val tab = AsciiTable().apply {
            addRule()
            addRow("What", "Where")
            addRule()
            addRow("image (${info.outerFsType})", fileName)
            ("${workDir}$stem.ext4").let { ext4 ->
                if (File(ext4).exists()) {
                    addRule()
                    addRow("converted image (ext4)", ext4)
                }
            }
            ("${workDir}$stem.erofs").let {
                if (File(it).exists()) {
                    addRule()
                    addRow("converted image (erofs)", it)
                    tail.addRule()
                    tail.addRow("sudo mount $it -o loop -t erofs ${workDir}mount")
                    tail.addRule()
                } else if (info.innerFsType == "erofs") {
                    tail.addRule()
                    tail.addRow("sudo mount $fileName -o loop -t erofs ${workDir}mount")
                    tail.addRule()
                }
            }
            ("${workDir}$stem").let {
                if (File(it).exists()) {
                    addRule()
                    if (File(it).isFile) {
                        addRow("converted image (raw)", it)
                    } else {
                        addRow("extracted content", it)
                    }
                }
            }
            ("${workDir}$stem.log").let {
                if (File(it).exists()) {
                    addRule()
                    addRow("log", it)
                }
            }
            if (info.innerFsType == "erofs") {
                addRule()
                addRow("mount point", "${workDir}mount")
            }
            addRule()
        }
        log.info("\n" + tab.render() + "\n" + if (info.innerFsType == "erofs") tail.render() else "")
    }

    fun updateVbmeta(): SparseImage {
        Avb.updateVbmeta(info.pulp, HashTreeDescriptor::class)
        return this
    }

    fun unwrap(): SparseImage {
        if (info.outerFsType == "sparse") {
            img2simg(workDir + File(info.output).name + ".signed", File(info.output).name + ".signed")
        } else {
            val s = info.pulp + ".signed"
            val t = info.output + ".signed"
            log.info("Moving $s -> $t")
            Files.move(Path(s), Path(t), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            File(info.pulp).deleleIfExists()
        }
        return this
    }

    data class SparseInfo(
        var output: String = "",
        var pulp: String = "",
        var json: String = "",
        var outerFsType: String = "raw",
        var innerFsType: String = "raw"
    )

    companion object {
        private val SPARSE_MAGIC: UInt = 0x3aff26edu
        private val log = LoggerFactory.getLogger(SparseImage::class.java)
        private val workDir = Helper.prop("workDir")!!
        private val simg2imgBin = "simg2img"
        private val img2simgBin = "img2simg"

        fun parse(fileName: String): SparseImage {
            val ret = SparseImage()
            ret.info.json = File(fileName).name.removeSuffix(".img") + ".json"
            ret.info.output = fileName
            ret.info.pulp = workDir + fileName
            if (isSparse(fileName)) {
                val tempFile = UUID.randomUUID().toString()
                ret.info.outerFsType = "sparse"
                val rawFile = "${workDir}${File(fileName).nameWithoutExtension}"
                simg2img(fileName, tempFile)
                ret.info.pulp = if (isExt4(tempFile)) {
                    ret.info.innerFsType = "ext4"
                    "$rawFile.ext4"
                } else if (isErofs(tempFile)) {
                    ret.info.innerFsType = "erofs"
                    "$rawFile.erofs"
                } else {
                    "$rawFile.raw"
                }
                Files.move(Path(tempFile), Path(ret.info.pulp))
            } else if (isExt4(fileName)) {
                ret.info.outerFsType = "ext4"
                ret.info.innerFsType = "ext4"
                File(fileName).copyTo(File(ret.info.pulp))
            } else if (isErofs(fileName)) {
                ret.info.outerFsType = "erofs"
                ret.info.innerFsType = "erofs"
                File(fileName).copyTo(File(ret.info.pulp))
            }
            when (ret.info.innerFsType) {
                "ext4" -> {
                    extractExt4(ret.info.pulp)
                }

                "erofs" -> {
                    extractErofs(ret.info.pulp)
                }

                else -> {
                    log.warn("unsuported image type: ${ret.info.innerFsType}")
                }
            }
            ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(File("${workDir}/${ret.info.json}"), ret)
            File("${workDir}mount").mkdir()
            extractVBMeta(ret.info.pulp)
            generateFileContexts()

            return ret
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
                val outStr = "7z x $fileName -y -o${workDir}$stem".check_output()
                File("${workDir}/$stem.log").writeText(outStr)
            } else {
                log.warn("Please install 7z for ext4 extraction")
            }
        }

        private fun extractErofs(fileName: String) {
            log.info("sudo mount $fileName -o loop -t erofs ${workDir}mount")
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

        fun extractVBMeta(fileName: String) {
            // vbmeta in image
            try {
                val ai = AVBInfo.parseFrom(Dumpling(fileName)).dumpDefault(fileName)
                if (File("vbmeta.img").exists()) {
                    log.warn("Found vbmeta.img, parsing ...")
                    VBMetaParser().unpack("vbmeta.img")
                }
            } catch (e: IllegalArgumentException) {
                log.warn(e.message)
                log.warn("failed to parse vbmeta info")
            }
        }

        fun generateFileContexts() {
            val env = EnvironmentUtils.getProcEnvironment().apply {
                put("PATH", "aosp/plugged/bin:" + System.getenv("PATH"))
                put("LD_LIBRARY_PATH", "aosp/plugged/lib:" + System.getenv("LD_LIBRARY_PATH"))
            }
            DefaultExecutor().apply {
                streamHandler = PumpStreamHandler(System.out, System.err)
            }.execute(CommandLine.parse("aosp/plugged/bin/sefcontext_compile").apply {
                addArguments("-o " + Helper.prop("workDir") + "file_contexts.bin")
                addArgument("aosp/plugged/res/file_contexts.concat")
            }.also { log.warn(it.toString()) }, env)
        }
    }
}
