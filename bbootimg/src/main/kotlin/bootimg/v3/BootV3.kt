package cfig.bootimg.v3

import cfig.Avb
import cfig.helper.Helper
import cfig.bootimg.Common.Companion.deleleIfExists
import cfig.bootimg.Common.Companion.getPaddingSize
import cfig.bootimg.Signer
import cfig.packable.VBMetaParser
import com.fasterxml.jackson.databind.ObjectMapper
import de.vandermeer.asciitable.AsciiTable
import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import cfig.bootimg.Common as C

@OptIn(ExperimentalUnsignedTypes::class)
data class BootV3(var info: MiscInfo = MiscInfo(),
                  var kernel: CommArgs = CommArgs(),
                  val ramdisk: CommArgs = CommArgs()
) {
    companion object {
        private val log = LoggerFactory.getLogger(BootV3::class.java)
        private val workDir = Helper.prop("workDir")

        fun parse(fileName: String): BootV3 {
            val ret = BootV3()
            FileInputStream(fileName).use { fis ->
                val header = BootHeaderV3(fis)
                //info
                ret.info.output = File(fileName).name
                ret.info.json = File(fileName).name.removeSuffix(".img") + ".json"
                ret.info.cmdline = header.cmdline
                ret.info.headerSize = header.headerSize
                ret.info.headerVersion = header.headerVersion
                ret.info.osVersion = header.osVersion
                ret.info.osPatchLevel = header.osPatchLevel
                ret.info.pageSize = BootHeaderV3.pageSize
                //kernel
                ret.kernel.file = workDir + "kernel"
                ret.kernel.size = header.kernelSize
                ret.kernel.position = BootHeaderV3.pageSize
                //ramdisk
                ret.ramdisk.file = workDir + "ramdisk.img"
                ret.ramdisk.size = header.ramdiskSize
                ret.ramdisk.position = ret.kernel.position + header.kernelSize +
                        getPaddingSize(header.kernelSize, BootHeaderV3.pageSize)
            }
            ret.info.imageSize = File(fileName).length()
            return ret
        }
    }

    data class MiscInfo(
            var output: String = "",
            var json: String = "",
            var headerVersion: Int = 0,
            var headerSize: Int = 0,
            var pageSize: Int = 0,
            var cmdline: String = "",
            var osVersion: String = "",
            var osPatchLevel: String = "",
            var imageSize: Long = 0
    )

    data class CommArgs(
            var file: String = "",
            var position: Int = 0,
            var size: Int = 0)

    fun pack(): BootV3 {
        if (File(this.ramdisk.file).exists() && !File(workDir + "root").exists()) {
            //do nothing if we have ramdisk.img.gz but no /root
            log.warn("Use prebuilt ramdisk file: ${this.ramdisk.file}")
        } else {
            File(this.ramdisk.file).deleleIfExists()
            File(this.ramdisk.file.replaceFirst("[.][^.]+$", "")).deleleIfExists()
            //TODO: remove cpio in C/C++
            //C.packRootfs("$workDir/root", this.ramdisk.file, C.parseOsMajor(info.osVersion))
            // enable advance JAVA cpio
            C.packRootfs("$workDir/root", this.ramdisk.file)
        }
        this.kernel.size = File(this.kernel.file).length().toInt()
        this.ramdisk.size = File(this.ramdisk.file).length().toInt()

        //header
        FileOutputStream(this.info.output + ".clear", false).use { fos ->
            val encodedHeader = this.toHeader().encode()
            fos.write(encodedHeader)
            fos.write(ByteArray(
                    Helper.round_to_multiple(encodedHeader.size,
                            this.info.pageSize) - encodedHeader.size))
        }

        //data
        log.info("Writing data ...")
        val bf = ByteBuffer.allocate(1024 * 1024 * 64)//assume total SIZE small than 64MB
        bf.order(ByteOrder.LITTLE_ENDIAN)
        C.writePaddedFile(bf, this.kernel.file, this.info.pageSize)
        C.writePaddedFile(bf, this.ramdisk.file, this.info.pageSize)
        //write
        FileOutputStream("${this.info.output}.clear", true).use { fos ->
            fos.write(bf.array(), 0, bf.position())
        }

        //google way
        this.toCommandLine().addArgument(this.info.output + ".google").let {
            log.info(it.toString())
            DefaultExecutor().execute(it)
        }

        C.assertFileEquals(this.info.output + ".clear", this.info.output + ".google")
        return this
    }

    fun sign(fileName: String): BootV3 {
        val avbtool = String.format(Helper.prop("avbtool"), "v1.2")
        Signer.signAVB(fileName, this.info.imageSize, avbtool)
        return this
    }

    private fun toHeader(): BootHeaderV3 {
        return BootHeaderV3(
                kernelSize = kernel.size,
                ramdiskSize = ramdisk.size,
                headerVersion = info.headerVersion,
                osVersion = info.osVersion,
                osPatchLevel = info.osPatchLevel,
                headerSize = info.headerSize,
                cmdline = info.cmdline)
    }

    fun extractImages(): BootV3 {
        val workDir = Helper.prop("workDir")
        //info
        ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(File(workDir + this.info.json), this)
        //kernel
        C.dumpKernel(C.Slice(info.output, kernel.position, kernel.size, kernel.file))
        //ramdisk
        val fmt = C.dumpRamdisk(
                C.Slice(info.output, ramdisk.position, ramdisk.size, ramdisk.file), "${workDir}root")
        this.ramdisk.file = this.ramdisk.file + ".$fmt"
        //dump info again
        ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(File(workDir + this.info.json), this)
        return this
    }

    fun extractVBMeta(): BootV3 {
        Avb().parseVbMeta(info.output)
        if (File("vbmeta.img").exists()) {
            log.warn("Found vbmeta.img, parsing ...")
            VBMetaParser().unpack("vbmeta.img")
        }
        return this
    }

    fun printSummary(): BootV3 {
        val workDir = Helper.prop("workDir")
        val tableHeader = AsciiTable().apply {
            addRule()
            addRow("What", "Where")
            addRule()
        }
        val tab = AsciiTable().let {
            it.addRule()
            it.addRow("image info", workDir + info.output.removeSuffix(".img") + ".json")
            it.addRule()
            it.addRow("kernel", this.kernel.file)
            File(Helper.prop("kernelVersionFile")).let { kernelVersionFile ->
                if (kernelVersionFile.exists()) {
                    it.addRow("\\-- version " + kernelVersionFile.readLines().toString(), kernelVersionFile.path)
                }
            }
            File(Helper.prop("kernelConfigFile")).let { kernelConfigFile ->
                if (kernelConfigFile.exists()) {
                    it.addRow("\\-- config", kernelConfigFile.path)
                }
            }
            it.addRule()
            it.addRow("ramdisk", this.ramdisk.file)
            it.addRow("\\-- extracted ramdisk rootfs", "${workDir}root")
            it.addRule()
            it.addRow("AVB info", Avb.getJsonFileName(info.output))
            it.addRule()
            it
        }
        val tabVBMeta = AsciiTable().let {
            if (File("vbmeta.img").exists()) {
                it.addRule()
                it.addRow("vbmeta.img", Avb.getJsonFileName("vbmeta.img"))
                it.addRule()
                "\n" + it.render()
            } else {
                ""
            }
        }
        log.info("\n\t\t\tUnpack Summary of ${info.output}\n{}\n{}{}",
                tableHeader.render(), tab.render(), tabVBMeta)
        return this
    }

    private fun toCommandLine(): CommandLine {
        return CommandLine("python").let { ret ->
            ret.addArgument(Helper.prop("mkbootimg"))
            ret.addArgument("--header_version")
            ret.addArgument(info.headerVersion.toString())
            if (kernel.size > 0) {
                ret.addArgument("--kernel")
                ret.addArgument(this.kernel.file)
            }
            if (ramdisk.size > 0) {
                ret.addArgument("--ramdisk")
                ret.addArgument(this.ramdisk.file)
            }
            if (info.cmdline.isNotBlank()) {
                ret.addArgument(" --cmdline ")
                ret.addArgument(info.cmdline, false)
            }
            if (info.osVersion.isNotBlank()) {
                ret.addArgument(" --os_version")
                ret.addArgument(info.osVersion)
            }
            if (info.osPatchLevel.isNotBlank()) {
                ret.addArgument(" --os_patch_level")
                ret.addArgument(info.osPatchLevel)
            }
            ret.addArgument(" --id ")
            ret.addArgument(" --output ")
            //ret.addArgument("boot.img" + ".google")

            log.debug("To Commandline: $ret")
            ret
        }
    }
}
