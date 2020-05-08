package cfig.bootimg.v3

import cfig.Avb
import cfig.Helper
import cfig.bootimg.Common.Companion.deleleIfExists
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
data class VendorBoot(var info: MiscInfo = MiscInfo(),
                      var ramdisk: CommArgs = CommArgs(),
                      var dtb: CommArgs = CommArgs()) {
    data class CommArgs(
            var file: String = "",
            var position: UInt = 0U,
            var size: UInt = 0U,
            var loadAddr: UInt = 0U)

    data class MiscInfo(
            var output: String = "",
            var json: String = "",
            var headerVersion: UInt = 0U,
            var product: String = "",
            var headerSize: UInt = 0U,
            var pageSize: UInt = 0U,
            var cmdline: String = "",
            var tagsLoadAddr: UInt = 0U,
            var kernelLoadAddr: UInt = 0U,
            var imageSize: Long = 0
    )

    companion object {
        private val log = LoggerFactory.getLogger(VendorBoot::class.java)
        fun parse(fileName: String): VendorBoot {
            val ret = VendorBoot()
            val workDir = Helper.prop("workDir")
            FileInputStream(fileName).use { fis ->
                val header = VendorBootHeader(fis)
                ret.info.output = File(fileName).name
                ret.info.json = File(fileName).name.removeSuffix(".img") + ".json"
                ret.info.headerSize = header.headerSize
                ret.info.product = header.product
                ret.info.tagsLoadAddr = header.tagsLoadAddr
                ret.info.cmdline = header.cmdline
                ret.info.kernelLoadAddr = header.kernelLoadAddr
                ret.info.pageSize = header.pageSize
                ret.info.headerVersion = header.headerVersion
                //ramdisk
                ret.ramdisk.file = workDir + "ramdisk.img.gz"
                ret.ramdisk.size = header.vndRamdiskSize
                ret.ramdisk.loadAddr = header.ramdiskLoadAddr
                ret.ramdisk.position = Helper.round_to_multiple(
                        VendorBootHeader.VENDOR_BOOT_IMAGE_HEADER_V3_SIZE,
                        header.pageSize)
                //dtb
                ret.dtb.file = workDir + "dtb"
                ret.dtb.size = header.dtbSize
                ret.dtb.loadAddr = header.dtbLoadAddr.toUInt()
                ret.dtb.position = ret.ramdisk.position +
                        Helper.round_to_multiple(ret.ramdisk.size, header.pageSize)
            }
            ret.info.imageSize = File(fileName).length()
            return ret
        }
    }

    fun pack(): VendorBoot {
        val workDir = Helper.prop("workDir")
        if (File(workDir + this.ramdisk.file).exists() && !File(workDir + "root").exists()) {
            //do nothing if we have ramdisk.img.gz but no /root
            log.warn("Use prebuilt ramdisk file: ${this.ramdisk.file}")
        } else {
            File(this.ramdisk.file).deleleIfExists()
            File(this.ramdisk.file.removeSuffix(".gz")).deleleIfExists()
            C.packRootfs("$workDir/root", this.ramdisk.file)
        }
        this.ramdisk.size = File(this.ramdisk.file).length().toUInt()
        this.dtb.size = File(this.dtb.file).length().toUInt()
        //header
        FileOutputStream(this.info.output + ".clear", false).use { fos ->
            val encodedHeader = this.toHeader().encode()
            fos.write(encodedHeader)
            fos.write(ByteArray((
                    Helper.round_to_multiple(encodedHeader.size.toUInt(),
                            this.info.pageSize) - encodedHeader.size.toUInt()).toInt()
            ))
        }
        //data
        log.info("Writing data ...")
        val bf = ByteBuffer.allocate(1024 * 1024 * 128)//assume total SIZE small than 64MB
        bf.order(ByteOrder.LITTLE_ENDIAN)
        C.writePaddedFile(bf, this.ramdisk.file, this.info.pageSize)
        C.writePaddedFile(bf, this.dtb.file, this.info.pageSize)
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

    fun sign(): VendorBoot {
        Signer.signAVB(info.output, this.info.imageSize)
        return this
    }

    private fun toHeader(): VendorBootHeader {
        return VendorBootHeader(
                headerVersion = info.headerVersion,
                pageSize = info.pageSize,
                kernelLoadAddr = info.kernelLoadAddr,
                ramdiskLoadAddr = ramdisk.loadAddr,
                vndRamdiskSize = ramdisk.size,
                cmdline = info.cmdline,
                tagsLoadAddr = info.tagsLoadAddr,
                product = info.product,
                headerSize = info.headerSize,
                dtbSize = dtb.size,
                dtbLoadAddr = dtb.loadAddr.toULong()
        )
    }

    fun extractImages(): VendorBoot {
        val workDir = Helper.prop("workDir")
        //header
        ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(
                File(workDir + this.info.json), this)
        //ramdisk
        C.dumpRamdisk(C.Slice(info.output, ramdisk.position.toInt(), ramdisk.size.toInt(), ramdisk.file),
                "${workDir}root")
        //dtb
        C.dumpDtb(C.Slice(info.output, dtb.position.toInt(), dtb.size.toInt(), dtb.file))
        return this
    }

    fun extractVBMeta(): VendorBoot {
        Avb().parseVbMeta(info.output)
        if (File("vbmeta.img").exists()) {
            log.warn("Found vbmeta.img, parsing ...")
            VBMetaParser().unpack("vbmeta.img")
        }
        return this
    }

    fun printSummary(): VendorBoot {
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
            it.addRow("ramdisk", this.ramdisk.file)
            it.addRow("\\-- extracted ramdisk rootfs", "${workDir}root")
            it.addRule()
            it.addRow("dtb", this.dtb.file)
            if (File(this.dtb.file + ".src").exists()) {
                it.addRow("\\-- decompiled dts", dtb.file + ".src")
            }
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
        return CommandLine(Helper.prop("mkbootimg"))
                .addArgument("--vendor_ramdisk")
                .addArgument(this.ramdisk.file)
                .addArgument("--dtb")
                .addArgument(this.dtb.file)
                .addArgument("--vendor_cmdline")
                .addArgument(info.cmdline, false)
                .addArgument("--header_version")
                .addArgument(info.headerVersion.toString())
                .addArgument("--vendor_boot")
    }
}
