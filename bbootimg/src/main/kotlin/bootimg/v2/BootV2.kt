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

package cfig.bootimg.v2

import avb.AVBInfo
import cfig.Avb
import cfig.bootimg.Common
import cfig.bootimg.Common.Companion.deleleIfExists
import cfig.bootimg.Common.Companion.shortenPath
import cfig.bootimg.Signer
import cfig.helper.Dumpling
import cfig.helper.Helper
import cfig.helper.ZipHelper
import cfig.packable.VBMetaParser
import cfig.utils.EnvironmentVerifier
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.freva.asciitable.HorizontalAlign
import de.vandermeer.asciitable.AsciiTable
import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.slf4j.LoggerFactory
import rom.fdt.DTC
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import cfig.bootimg.Common as C

data class BootV2(
    var info: MiscInfo = MiscInfo(),
    var kernel: CommArgs = CommArgs(),
    var ramdisk: RamdiskArgs = RamdiskArgs(),
    var secondBootloader: CommArgs? = null,
    var recoveryDtbo: CommArgsLong? = null,
    var dtb: DtbArgsLong? = null,
) {
    data class MiscInfo(
        var output: String = "",
        var json: String = "",
        var headerVersion: Int = 0,
        var headerSize: Int = 0,
        var loadBase: Long = 0,
        var tagsOffset: Long = 0,
        var board: String? = null,
        var pageSize: Int = 0,
        var cmdline: String = "",
        var osVersion: String? = null,
        var osPatchLevel: String? = null,
        var hash: ByteArray? = byteArrayOf(),
        var verify: String = "",
        var imageSize: Long = 0,
    )

    data class CommArgs(
        var file: String? = null,
        var position: Long = 0,
        var size: Int = 0,
        var loadOffset: Long = 0,
    )

    data class RamdiskArgs(
        var file: String? = null,
        var position: Long = 0,
        var size: Int = 0,
        var loadOffset: Long = 0,
        var xzFlags: String? = null
    )

    data class CommArgsLong(
        var file: String? = null,
        var position: Long = 0,
        var size: Int = 0,
        var loadOffset: Long = 0,
    )

    data class DtbArgsLong(
        var file: String? = null,
        var position: Long = 0,
        var size: Int = 0,
        var loadOffset: Long = 0,
        var dtbList: MutableList<DTC.DtbEntry> = mutableListOf(),
    )

    companion object {
        private val log = LoggerFactory.getLogger(BootV2::class.java)
        private val workDir: () -> String = {
            Helper.prop("workDir")!!
        }
        private val mapper = ObjectMapper()
        private val dtsSuffix = Helper.prop("config.dts_suffix")

        fun parse(fileName: String): BootV2 {
            val ret = BootV2()
            FileInputStream(fileName).use { fis ->
                val bh2 = BootHeaderV2(fis)
                ret.info.let { theInfo ->
                    theInfo.output = File(fileName).name
                    theInfo.json = File(fileName).name.removeSuffix(".img") + ".json"
                    theInfo.pageSize = bh2.pageSize
                    theInfo.headerSize = bh2.headerSize
                    theInfo.headerVersion = bh2.headerVersion
                    theInfo.board = bh2.board
                    theInfo.cmdline = bh2.cmdline
                    theInfo.imageSize = File(fileName).length()
                    theInfo.tagsOffset = bh2.tagsOffset
                    theInfo.hash = bh2.hash
                    theInfo.osVersion = bh2.osVersion
                    theInfo.osPatchLevel = bh2.osPatchLevel
                    if (Avb.hasAvbFooter(fileName)) {
                        theInfo.verify = "VB2.0"
                        if (Avb.verifyAVBIntegrity(fileName, String.format(Helper.prop("avbtool")!!, "v1.2"))) {
                            theInfo.verify += " PASS"
                        } else {
                            theInfo.verify += " FAIL"
                        }
                    } else {
                        theInfo.verify = "VB1.0"
                    }
                }
                ret.kernel.let { theKernel ->
                    theKernel.file = File(workDir(), "kernel").path
                    theKernel.size = bh2.kernelLength
                    theKernel.loadOffset = bh2.kernelOffset
                    theKernel.position = ret.getKernelPosition()
                }
                ret.ramdisk.let { theRamdisk ->
                    theRamdisk.size = bh2.ramdiskLength
                    theRamdisk.loadOffset = bh2.ramdiskOffset
                    theRamdisk.position = ret.getRamdiskPosition()
                    if (bh2.ramdiskLength > 0) {
                        theRamdisk.file = File(workDir(), "ramdisk.img").path
                    }
                }
                if (bh2.secondBootloaderLength > 0) {
                    ret.secondBootloader = CommArgs()
                    ret.secondBootloader!!.size = bh2.secondBootloaderLength
                    ret.secondBootloader!!.loadOffset = bh2.secondBootloaderOffset
                    ret.secondBootloader!!.file = File(workDir(), "second").path
                    ret.secondBootloader!!.position = ret.getSecondBootloaderPosition()
                }
                if (bh2.recoveryDtboLength > 0) {
                    ret.recoveryDtbo = CommArgsLong()
                    ret.recoveryDtbo!!.size = bh2.recoveryDtboLength
                    ret.recoveryDtbo!!.loadOffset = bh2.recoveryDtboOffset //Q
                    ret.recoveryDtbo!!.file = File(workDir(), "recoveryDtbo").path
                    ret.recoveryDtbo!!.position = ret.getRecoveryDtboPosition()
                }
                if (bh2.dtbLength > 0) {
                    ret.dtb = DtbArgsLong()
                    ret.dtb!!.size = bh2.dtbLength
                    ret.dtb!!.loadOffset = bh2.dtbOffset //Q
                    ret.dtb!!.file = File(workDir(), "dtb").path
                    ret.dtb!!.position = ret.getDtbPosition()
                }
            }
            return ret
        }
    }

    private fun getHeaderSize(pageSize: Int): Int {
        val pad = (pageSize - (1648 and (pageSize - 1))) and (pageSize - 1)
        return pad + 1648
    }

    private fun getKernelPosition(): Long {
        return getHeaderSize(info.pageSize).toLong()
    }

    private fun getRamdiskPosition(): Long {
        return (getKernelPosition() + kernel.size +
                Common.getPaddingSize(kernel.size, info.pageSize))
    }

    private fun getSecondBootloaderPosition(): Long {
        return getRamdiskPosition() + ramdisk.size +
                Common.getPaddingSize(ramdisk.size, info.pageSize)
    }

    private fun getRecoveryDtboPosition(): Long {
        return if (this.secondBootloader == null) {
            getSecondBootloaderPosition()
        } else {
            getSecondBootloaderPosition() + secondBootloader!!.size +
                    Common.getPaddingSize(secondBootloader!!.size, info.pageSize)
        }
    }

    private fun getDtbPosition(): Long {
        return if (this.recoveryDtbo == null) {
            getRecoveryDtboPosition()
        } else {
            getRecoveryDtboPosition() + recoveryDtbo!!.size +
                    Common.getPaddingSize(recoveryDtbo!!.size, info.pageSize)
        }
    }

    fun extractImages(): BootV2 {
        //info
        mapper.writerWithDefaultPrettyPrinter().writeValue(File(workDir(), info.json), this)
        //kernel
        if (kernel.size > 0) {
            Common.dumpKernel(Helper.Slice(info.output, kernel.position.toInt(), kernel.size, kernel.file!!))
        } else {
            kernel.file = null
            log.warn("found boot image without kernel: ${this.info.output}")
            check(info.output == "ramdisk.img") {
                "This should not happen, please add comments at: https://github.com/cfig/Android_boot_image_editor/issues/122"
            }
        }
        //ramdisk
        if (this.ramdisk.size > 0) {
            val fmt = C.dumpRamdisk(
                Helper.Slice(info.output, ramdisk.position.toInt(), ramdisk.size, ramdisk.file!!),
                File(workDir(), "root").path
            )
            this.ramdisk.file = this.ramdisk.file!! + ".$fmt"
            if (fmt == "xz") {
                val checkType =
                    ZipHelper.xzStreamFlagCheckTypeToString(ZipHelper.parseStreamFlagCheckType(this.ramdisk.file!!))
                this.ramdisk.xzFlags = checkType
            }
            //dump info again
            mapper.writerWithDefaultPrettyPrinter().writeValue(File(workDir(), this.info.json), this)
        }
        //second bootloader
        secondBootloader?.let {
            Helper.extractFile(
                info.output,
                secondBootloader!!.file!!,
                secondBootloader!!.position,
                secondBootloader!!.size
            )
        }
        //recovery dtbo
        recoveryDtbo?.let {
            Helper.extractFile(
                info.output,
                recoveryDtbo!!.file!!,
                recoveryDtbo!!.position,
                recoveryDtbo!!.size
            )
        }
        //dtb
        this.dtb?.let { _ ->
            Common.dumpDtb(Helper.Slice(info.output, dtb!!.position.toInt(), dtb!!.size, dtb!!.file!!), false)
            this.dtb!!.dtbList = DTC.parseMultiple(dtb!!.file!!)
            log.info("dtb sz = " + this.dtb!!.dtbList.size)
            //dump info again
            mapper.writerWithDefaultPrettyPrinter().writeValue(File(workDir(), info.json), this)

            //dump dtb items
            DTC.extractMultiple(dtb!!.file!!, this.dtb!!.dtbList)
        }

        return this
    }

    fun extractVBMeta(): BootV2 {
        if (this.info.verify.startsWith("VB2.0")) {
            AVBInfo.parseFrom(Dumpling(info.output)).dumpDefault(info.output)
            if (File("vbmeta.img").exists()) {
                log.warn("Found vbmeta.img, parsing ...")
                VBMetaParser().unpack("vbmeta.img")
            }
        } else {
            log.info("verify type is ${this.info.verify}, skip AVB parsing")
        }
        return this
    }

    fun printUnpackSummary(): BootV2 {
        val prints: MutableList<Pair<String, String>> = mutableListOf()
        val tableHeader = AsciiTable().apply {
            addRule()
            addRow("What", "Where")
            addRule()
        }
        val tab = AsciiTable().let {
            it.addRule()
            it.addRow("image info", shortenPath( File(Helper.prop("workDir"), info.output.removeSuffix(".img") + ".json").path))
            prints.add(Pair("image info", shortenPath(File(workDir(), info.output.removeSuffix(".img") + ".json").path)))
            if (this.info.verify.startsWith("VB2.0")) {
                it.addRule()
                val verifyStatus = if (this.info.verify.contains("PASS")) {
                    "verified"
                } else {
                    "verify fail"
                }
                Avb.getJsonFileName(info.output).let { jsonFile ->
                    it.addRow("AVB info [$verifyStatus]", shortenPath(jsonFile))
                    prints.add(Pair("AVB info [$verifyStatus]", shortenPath(jsonFile)))
                    if (File(jsonFile).exists()) {
                        mapper.readValue(File(jsonFile), AVBInfo::class.java).let { ai ->
                            val inspectRet = Avb.inspectKey(ai)
                            if (inspectRet != "NONE") {
                                it.addRow("\\-- signing key", inspectRet)
                                prints.add(Pair("\\-- signing key", inspectRet))
                            }
                        }
                    }
                }
            }
            //kernel
            it.addRule()
            if (this.kernel.size > 0) {
                it.addRow("kernel", shortenPath(this.kernel.file!!))
            } else { //only for ramdisk.img, Issue #122
                it.addRow("kernel", "NONE")
            }
            prints.add(Pair("kernel", shortenPath(this.kernel.file.toString())))
            File(Helper.prop("kernelVersionFile")).let { kernelVersionFile ->
                if (kernelVersionFile.exists()) {
                    it.addRow("\\-- version " + kernelVersionFile.readLines().toString(), kernelVersionFile.path)
                    prints.add(Pair("\\-- version " + kernelVersionFile.readLines().toString(), kernelVersionFile.path))
                }
            }
            File(Helper.prop("kernelConfigFile")).let { kernelConfigFile ->
                if (kernelConfigFile.exists()) {
                    it.addRow("\\-- config", kernelConfigFile.path)
                    prints.add(Pair("\\-- config", kernelConfigFile.path))
                }
            }
            //ramdisk
            if (this.ramdisk.size > 0) {
                it.addRule()
                it.addRow("ramdisk", shortenPath(this.ramdisk.file!!))
                prints.add(Pair("ramdisk", shortenPath(this.ramdisk.file.toString())))
                it.addRow("\\-- extracted ramdisk rootfs", shortenPath(File(workDir(), "root").path))
                prints.add(Pair("\\-- extracted ramdisk rootfs", shortenPath(File(workDir(), "root").path)))
            }
            //second
            this.secondBootloader?.let { theSecondBootloader ->
                if (theSecondBootloader.size > 0) {
                    it.addRule()
                    it.addRow("second bootloader", theSecondBootloader.file)
                    prints.add(Pair("second bootloader", theSecondBootloader.file.toString()))
                }
            }
            //dtbo
            this.recoveryDtbo?.let { theDtbo ->
                if (theDtbo.size > 0) {
                    it.addRule()
                    it.addRow("recovery dtbo", theDtbo.file)
                    prints.add(Pair("recovery dtbo", theDtbo.file.toString()))
                }
            }
            //dtb
            this.dtb?.let { theDtb ->
                if (theDtb.size > 0) {
                    val dtbCount = this.dtb!!.dtbList.size
                    it.addRule()
                    it.addRow("dtb", theDtb.file?.let { fullPath -> shortenPath(fullPath) })
                    prints.add(Pair("dtb", shortenPath(theDtb.file.toString())))
                    if (File(theDtb.file + ".0.${dtsSuffix}").exists()) {
                        it.addRow("\\-- decompiled dts [$dtbCount]", shortenPath(theDtb.file!!) + ".*.${dtsSuffix}")
                        prints.add(Pair("\\-- decompiled dts [$dtbCount]", shortenPath(theDtb.file!!) + ".*.${dtsSuffix}"))
                    }
                }
            }
            //END
            it.addRule()
            it
        }
        val tabVBMeta = AsciiTable().let {
            if (File("vbmeta.img").exists()) {
                it.addRule()
                it.addRow("vbmeta.img", shortenPath(Avb.getJsonFileName("vbmeta.img")))
                prints.add(Pair("vbmeta.img", shortenPath(Avb.getJsonFileName("vbmeta.img"))))
                it.addRule()
                "\n" + it.render()
            } else {
                ""
            }
        }

        if (EnvironmentVerifier().isWindows) {
            log.info(
                "\n" +
                        com.github.freva.asciitable.AsciiTable.getTable(
                            com.github.freva.asciitable.AsciiTable.BASIC_ASCII,
                            prints, mutableListOf(
                                com.github.freva.asciitable.Column().header("What")
                                    .dataAlign(HorizontalAlign.LEFT)
                                    .with { it.first },
                                com.github.freva.asciitable.Column().header("Where").with { it.second })
                        )
            )
        } else {
            log.info(
                "\n\t\t\tUnpack Summary of ${info.output}\n{}\n{}{}",
                tableHeader.render(), tab.render(), tabVBMeta
            )
        }

        return this
    }

    private fun toHeader(): BootHeaderV2 {
        return BootHeaderV2(
            kernelLength = kernel.size,
            kernelOffset = kernel.loadOffset,
            ramdiskLength = ramdisk.size,
            ramdiskOffset = ramdisk.loadOffset,
            secondBootloaderLength = if (secondBootloader != null) secondBootloader!!.size else 0,
            secondBootloaderOffset = if (secondBootloader != null) secondBootloader!!.loadOffset else 0,
            recoveryDtboLength = if (recoveryDtbo != null) recoveryDtbo!!.size else 0,
            recoveryDtboOffset = if (recoveryDtbo != null) recoveryDtbo!!.loadOffset else 0,
            dtbLength = if (dtb != null) dtb!!.size else 0,
            dtbOffset = if (dtb != null) dtb!!.loadOffset else 0,
            tagsOffset = info.tagsOffset,
            pageSize = info.pageSize,
            headerSize = info.headerSize,
            headerVersion = info.headerVersion,
            board = info.board.toString(),
            cmdline = info.cmdline,
            hash = info.hash,
            osVersion = info.osVersion,
            osPatchLevel = info.osPatchLevel
        )
    }

    fun pack(): BootV2 {
        //refresh kernel size
        this.kernel.size = if (this.kernel.file != null) File(this.kernel.file!!).length().toInt() else 0
        //refresh ramdisk size
        if (this.ramdisk.file.isNullOrBlank()) {
            ramdisk.file = null
            ramdisk.loadOffset = 0
        } else {
            if (File(this.ramdisk.file!!).exists() && !File(workDir(), "root").exists()) {
                //do nothing if we have ramdisk.img.gz but no /root
                log.warn("Use prebuilt ramdisk file: ${this.ramdisk.file}")
            } else {
                File(this.ramdisk.file!!).deleleIfExists()
                File(this.ramdisk.file!!.removeSuffix(".gz")).deleleIfExists()
                //Common.packRootfs("${workDir}/root", this.ramdisk.file!!, Common.parseOsMajor(info.osVersion.toString()))
                Common.packRootfs(File(workDir(), "root").path, this.ramdisk.file!!, this.ramdisk.xzFlags)
            }
            this.ramdisk.size = File(this.ramdisk.file!!).length().toInt()
        }
        //refresh second bootloader size
        secondBootloader?.let { theSecond ->
            theSecond.size = File(theSecond.file!!).length().toInt()
        }
        //refresh recovery dtbo size
        recoveryDtbo?.let { theDtbo ->
            theDtbo.size = File(theDtbo.file!!).length().toInt()
            theDtbo.loadOffset = getRecoveryDtboPosition()
            log.warn("using fake recoveryDtboOffset ${theDtbo.loadOffset} (as is in AOSP avbtool)")
        }
        //refresh dtb size
        dtb?.let { theDtb ->
            if (File(theDtb.file!! + ".0.${dtsSuffix}").exists()) {
                DTC.packMultiple(theDtb.file!!, theDtb.dtbList)
            }
            theDtb.size = File(theDtb.file!!).length().toInt()
        }
        //refresh image hash
        info.hash = when (info.headerVersion) {
            0 -> {
                Common.hashFileAndSize(kernel.file, ramdisk.file, secondBootloader?.file)
            }

            1 -> {
                Common.hashFileAndSize(
                    kernel.file, ramdisk.file,
                    secondBootloader?.file, recoveryDtbo?.file
                )
            }

            2 -> {
                Common.hashFileAndSize(
                    kernel.file, ramdisk.file,
                    secondBootloader?.file, recoveryDtbo?.file, dtb?.file
                )
            }

            else -> {
                throw IllegalArgumentException("headerVersion ${info.headerVersion} illegal")
            }
        }

        val encodedHeader = this.toHeader().encode()

        //write
        FileOutputStream("${info.output}.clear", false).use { fos ->
            fos.write(encodedHeader)
            fos.write(ByteArray((Helper.round_to_multiple(encodedHeader.size, info.pageSize) - encodedHeader.size)))
        }

        log.info("Writing data ...")
        //boot image size may > 64MB. Fix issue #57
        val bytesV2 = ByteBuffer.allocate(maxOf(1024 * 1024 * 64, info.imageSize.toInt()))
            .let { bf ->
                bf.order(ByteOrder.LITTLE_ENDIAN)
                if (kernel.size > 0) {
                    Common.writePaddedFile(bf, kernel.file!!, info.pageSize)
                }
                if (ramdisk.size > 0) {
                    Common.writePaddedFile(bf, ramdisk.file!!, info.pageSize)
                }
                secondBootloader?.let {
                    Common.writePaddedFile(bf, secondBootloader!!.file!!, info.pageSize)
                }
                recoveryDtbo?.let {
                    Common.writePaddedFile(bf, recoveryDtbo!!.file!!, info.pageSize)
                }
                dtb?.let {
                    Common.writePaddedFile(bf, dtb!!.file!!, info.pageSize)
                }
                bf
            }
        //write
        FileOutputStream("${info.output}.clear", true).use { fos ->
            fos.write(bytesV2.array(), 0, bytesV2.position())
        }

        this.toCommandLine().apply {
            addArgument("${info.output}.google")
            log.info(this.toString())
            DefaultExecutor().execute(this)
        }

        Helper.assertFileEquals("${info.output}.clear", "${info.output}.google")

        return this
    }

    private fun toCommandLine(): CommandLine {
        val cmdPrefix = if (EnvironmentVerifier().isWindows) "python " else ""
        val ret = CommandLine.parse(cmdPrefix + Helper.prop("mkbootimg"))
        ret.addArgument(" --header_version ")
        ret.addArgument(info.headerVersion.toString())
        ret.addArgument(" --base ")
        ret.addArgument("0x" + java.lang.Long.toHexString(0))
        if (kernel.size > 0) {
            ret.addArgument(" --kernel ")
            ret.addArgument(kernel.file!!)
        }
        ret.addArgument(" --kernel_offset ")
        ret.addArgument("0x" + Integer.toHexString(kernel.loadOffset.toInt()))
        if (this.ramdisk.size > 0) {
            ret.addArgument(" --ramdisk ")
            ret.addArgument(ramdisk.file)
        }
        ret.addArgument(" --ramdisk_offset ")
        ret.addArgument("0x" + Integer.toHexString(ramdisk.loadOffset.toInt()))
        if (secondBootloader != null) {
            ret.addArgument(" --second ")
            ret.addArgument(secondBootloader!!.file!!)
            ret.addArgument(" --second_offset ")
            ret.addArgument("0x" + Integer.toHexString(secondBootloader!!.loadOffset.toInt()))
        }
        if (!info.board.isNullOrBlank()) {
            ret.addArgument(" --board ")
            ret.addArgument(info.board)
        }
        if (info.headerVersion > 0) {
            if (recoveryDtbo != null) {
                ret.addArgument(" --recovery_dtbo ")
                ret.addArgument(recoveryDtbo!!.file!!)
            }
        }
        if (info.headerVersion > 1) {
            if (dtb != null) {
                ret.addArgument("--dtb ")
                ret.addArgument(dtb!!.file!!)
                ret.addArgument("--dtb_offset ")
                ret.addArgument("0x" + java.lang.Long.toHexString(dtb!!.loadOffset))
            }
        }
        ret.addArgument(" --pagesize ")
        ret.addArgument(info.pageSize.toString())
        ret.addArgument(" --cmdline ")
        ret.addArgument(info.cmdline, false)
        if (!info.osVersion.isNullOrBlank()) {
            ret.addArgument(" --os_version ")
            ret.addArgument(info.osVersion)
        }
        if (!info.osPatchLevel.isNullOrBlank()) {
            ret.addArgument(" --os_patch_level ")
            ret.addArgument(info.osPatchLevel)
        }
        ret.addArgument(" --tags_offset ")
        ret.addArgument("0x" + Integer.toHexString(info.tagsOffset.toInt()))
        ret.addArgument(" --id ")
        ret.addArgument(" --output ")
        //ret.addArgument("boot.img" + ".google")

        log.debug("To Commandline: $ret")

        return ret
    }

    fun sign(): BootV2 {
        //unify with v1.1/v1.2 avbtool
        val avbtool = String.format(Helper.prop("avbtool")!!, "v1.2")
        if (info.verify.startsWith("VB2.0")) {
            Signer.signAVB(info.output, this.info.imageSize, avbtool)
            log.info("Adding hash_footer with verified-boot 2.0 style")
        } else {
            Signer.signVB1(info.output + ".clear", info.output + ".signed")
        }
        return this
    }

    fun printPackSummary(): BootV2 {
        Common.printPackSummary(info.output)
        return this
    }

    fun updateVbmeta(): BootV2 {
        Avb.updateVbmeta(info.output)
        return this
    }
}
