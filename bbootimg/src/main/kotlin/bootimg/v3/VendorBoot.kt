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

package cfig.bootimg.v3

import avb.AVBInfo
import cc.cfig.io.Struct
import cfig.Avb
import cfig.bootimg.Common
import cfig.bootimg.Common.Companion.deleleIfExists
import cfig.bootimg.Common.Companion.toShortenPath
import cfig.bootimg.Signer
import cfig.helper.Dumpling
import cfig.helper.Helper
import cfig.helper.ZipHelper
import cfig.packable.VBMetaParser
import cfig.utils.EnvironmentVerifier
import com.fasterxml.jackson.databind.ObjectMapper
import de.vandermeer.asciitable.AsciiTable
import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.slf4j.LoggerFactory
import rom.fdt.DTC
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import cfig.bootimg.Common as C

data class VendorBoot(
    var info: MiscInfo = MiscInfo(),
    var ramdisk: RamdiskArgs = RamdiskArgs(),
    var dtb: DtbArgs = DtbArgs(),
    var ramdisk_table: Vrt = Vrt(),
    var bootconfig: CommArgs = CommArgs(),
) {
    data class CommArgs(
        var file: String = "",
        var position: Long = 0,
        var size: Int = 0,
        var loadAddr: Long = 0,
    )

    data class DtbArgs(
        var file: String = "",
        var position: Long = 0,
        var size: Int = 0,
        var loadAddr: Long = 0,
        var dtbList: MutableList<DTC.DtbEntry> = mutableListOf(),
    )

    data class RamdiskArgs(
        var file: String = "",
        var position: Long = 0,
        var size: Int = 0,
        var loadAddr: Long = 0,
        var xzFlags: String? = null
    )

    data class MiscInfo(
        var input: String = "",
        var role: String = "",
        var json: String = "",
        var headerVersion: Int = 0,
        var product: String = "",
        var headerSize: Int = 0,
        var pageSize: Int = 0,
        var cmdline: String = "",
        var tagsLoadAddr: Long = 0,
        var kernelLoadAddr: Long = 0,
        var imageSize: Long = 0,
    )

    enum class VrtType {
        NONE,
        PLATFORM,
        RECOVERY,
        DLKM;

        companion object {
            fun fromInt(value: Int): VrtType {
                return when (value) {
                    NONE.ordinal -> NONE
                    PLATFORM.ordinal -> PLATFORM
                    RECOVERY.ordinal -> RECOVERY
                    DLKM.ordinal -> DLKM
                    else -> throw IllegalArgumentException("illegal VrtType $value")
                }
            }
        }
    }

    class Vrt(
        var size: Int = 0,
        var eachEntrySize: Int = 0,
        var position: Long = 0,
        var ramdidks: MutableList<VrtEntry> = mutableListOf(),
    ) {
        fun update(): Vrt {
            var totalSz = 0
            this.ramdidks.forEachIndexed { _, vrtEntry ->
                vrtEntry.offset = totalSz
                vrtEntry.size = File(vrtEntry.file).length().toInt()
                totalSz += vrtEntry.size
            }
            return this
        }

        fun encode(inPageSize: Int): ByteArray {
            val bf = ByteBuffer.allocate(8192)
            this.ramdidks.forEach {
                bf.put(it.encode())
            }
            val realSize = bf.position()
            val ret = ByteArray(Helper.round_to_multiple(realSize, inPageSize))
            bf.array().copyInto(ret, 0, 0, realSize)
            return ret
        }
    }

    class VrtEntry(
        var size: Int = 0,
        var offset: Int = 0,
        var type: VrtType = VrtType.NONE,
        var name: String = "", //32s
        var boardId: ByteArray = byteArrayOf(), //16I (aka. 64 bytes)
        var boardIdStr: String = "",
        var file: String = "",
    ) {
        companion object {
            private const val VENDOR_RAMDISK_NAME_SIZE = 32
            const val VENDOR_RAMDISK_TABLE_ENTRY_BOARD_ID_SIZE = 16

            //const val FORMAT_STRING = "3I${VENDOR_RAMDISK_NAME_SIZE}s${VENDOR_RAMDISK_TABLE_ENTRY_BOARD_ID_SIZE}I"
            const val FORMAT_STRING = "3I${VENDOR_RAMDISK_NAME_SIZE}s${VENDOR_RAMDISK_TABLE_ENTRY_BOARD_ID_SIZE * 4}b"
            const val SIZE = 108

            init {
                check(Struct(FORMAT_STRING).calcSize() == SIZE)
            }
        }

        constructor(iS: InputStream?, dumpFile: String) : this() {
            if (iS == null) {
                return
            }
            val info = Struct(FORMAT_STRING).unpack(iS)
            check((3 + 1 + 1) == info.size)
            this.size = (info[0] as UInt).toInt()
            this.offset = (info[1] as UInt).toInt()
            this.type = VrtType.fromInt((info[2] as UInt).toInt())
            this.name = info[3] as String
            this.boardId = info[4] as ByteArray
            this.boardIdStr = Struct.StringFleet().get(boardId, ByteOrder.LITTLE_ENDIAN)
            this.file = dumpFile
        }

        fun encode(): ByteArray {
            return Struct(FORMAT_STRING).pack(this.size, this.offset, this.type.ordinal, this.name, this.boardId)
        }

        override fun toString(): String {
            return "VrtEntry(size=$size, offset=$offset, type=$type, name='$name', boardIdStr='$boardIdStr', file='$file')"
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(VendorBoot::class.java)
        private val workDir = Helper.prop("workDir")
        private val mapper = ObjectMapper()
        private val dtsSuffix = Helper.prop("config.dts_suffix")
        private val environmentVerifier = EnvironmentVerifier()
        fun parse(fileName: String): VendorBoot {
            val ret = VendorBoot()
            FileInputStream(fileName).use { fis ->
                val header = VendorBootHeader(fis)
                ret.info.input = File(fileName).canonicalPath
                ret.info.role = File(fileName).name
                ret.info.json = File(fileName).name.removeSuffix(".img") + ".json"
                ret.info.headerSize = header.headerSize
                ret.info.product = header.product
                ret.info.tagsLoadAddr = header.tagsLoadAddr
                ret.info.cmdline = header.cmdline.trim()
                ret.info.kernelLoadAddr = header.kernelLoadAddr
                ret.info.pageSize = header.pageSize
                ret.info.headerVersion = header.headerVersion
                //ramdisk
                ret.ramdisk.file = Helper.joinPath(workDir!!, "ramdisk.img")
                ret.ramdisk.size = header.vndRamdiskTotalSize
                ret.ramdisk.loadAddr = header.ramdiskLoadAddr
                ret.ramdisk.position = Helper.round_to_multiple(
                    VendorBootHeader.VENDOR_BOOT_IMAGE_HEADER_V3_SIZE, header.pageSize
                ).toLong()
                //dtb
                ret.dtb.file = Helper.joinPath(workDir, "dtb")
                ret.dtb.size = header.dtbSize
                ret.dtb.loadAddr = header.dtbLoadAddr
                ret.dtb.position = ret.ramdisk.position + Helper.round_to_multiple(ret.ramdisk.size, header.pageSize)
                //vrt
                if (header.vrtSize > 0) {
                    ret.ramdisk_table.size = header.vrtSize
                    ret.ramdisk_table.eachEntrySize = header.vrtEntrySize
                    ret.ramdisk_table.position =
                        ret.dtb.position + Helper.round_to_multiple(ret.dtb.size, header.pageSize)
                    FileInputStream(ret.info.input).use {
                        it.skip(ret.ramdisk_table.position)
                        for (item in 0 until header.vrtEntryNum) {
                            ret.ramdisk_table.ramdidks.add(VrtEntry(it, Helper.joinPath(workDir, "ramdisk.${item + 1}")))
                        }
                    }
                    ret.ramdisk_table.ramdidks.forEach {
                        log.warn(it.toString())
                    }
                }
                //bootconfig
                if (header.bootconfigSize > 0) {
                    ret.bootconfig.file = Helper.joinPath(workDir, "bootconfig")
                    ret.bootconfig.size = header.bootconfigSize
                    ret.bootconfig.position =
                        ret.ramdisk_table.position + Helper.round_to_multiple(ret.ramdisk_table.size, header.pageSize)
                }
            }
            ret.info.imageSize = File(fileName).length()
            return ret
        }
    }

    fun pack(): VendorBoot {
        when (this.info.headerVersion) {
            3 -> {
                if (File(workDir, this.ramdisk.file).exists() && !File(workDir, "root").exists()) {
                    //do nothing if we have ramdisk.img.gz but no /root
                    log.warn("Use prebuilt ramdisk file: ${this.ramdisk.file}")
                } else {
                    File(this.ramdisk.file).deleleIfExists()
                    File(this.ramdisk.file.removeSuffix(".gz")).deleleIfExists()
                    //Fixed: remove cpio in C/C++
                    //C.packRootfs("$workDir/root", this.ramdisk.file, parseOsMajor())
                    //enable advance JAVA cpio
                    C.packRootfs(Helper.joinPath("$workDir", "root"), this.ramdisk.file, this.ramdisk.xzFlags)
                }
                this.ramdisk.size = File(this.ramdisk.file).length().toInt()
            }
            else -> {
                this.ramdisk_table.ramdidks.forEachIndexed { index, it ->
                    File(it.file).deleleIfExists()
                    log.info(Helper.joinPath(workDir!!, "root.${index + 1}") + " -> " + it.file)
                    C.packRootfs(Helper.joinPath(workDir, "root.${index + 1}"), it.file, this.ramdisk.xzFlags)
                }
                this.ramdisk.size = this.ramdisk_table.ramdidks.sumOf { File(it.file).length() }.toInt()
            }
        }
        //update dtb
        if (File(this.dtb.file + ".0.${dtsSuffix}").exists()) {
            DTC.packMultiple(this.dtb.file, this.dtb.dtbList)
        }
        this.dtb.size = File(this.dtb.file).length().toInt()
        //header
        val clearFile = Helper.joinPath(Helper.prop("intermediateDir")!!, this.info.role + ".clear")
        val googleFile = Helper.joinPath(Helper.prop("intermediateDir")!!, this.info.role + ".google")
        FileOutputStream(clearFile, false).use { fos ->
            val encodedHeader = this.toHeader().encode()
            fos.write(encodedHeader)
            fos.write(ByteArray(Helper.round_to_multiple(encodedHeader.size, this.info.pageSize) - encodedHeader.size))
        }
        //data
        log.info("Writing data ...")
        val bf = when (this.info.headerVersion) {
            //assume total SIZE is smaller than 128MB
            3 -> {
                ByteBuffer.allocate(1024 * 1024 * 128).let {
                    it.order(ByteOrder.LITTLE_ENDIAN)
                    //1. vendor ramdisks and dtb
                    C.writePaddedFile(it, this.ramdisk.file, this.info.pageSize)
                    C.writePaddedFile(it, this.dtb.file, this.info.pageSize)
                    it
                }
            }
            else -> {
                ByteBuffer.allocate(1024 * 1024 * 128).let {
                    it.order(ByteOrder.LITTLE_ENDIAN)
                    //1. vendor ramdisks
                    C.writePaddedFiles(it, this.ramdisk_table.ramdidks.map { rd -> rd.file }, this.info.pageSize)
                    //2. dtb
                    if (this.dtb.size > 0) {
                        C.writePaddedFile(it, this.dtb.file, this.info.pageSize)
                    }
                    //3. vrt
                    it.put(this.ramdisk_table.update().encode(this.info.pageSize))
                    //4. bootconfig
                    if (this.bootconfig.file.isNotBlank()) {
                        C.writePaddedFile(it, this.bootconfig.file, this.info.pageSize)
                    }
                    it
                }
            }
        }
        //write
        FileOutputStream(clearFile, true).use { fos ->
            fos.write(bf.array(), 0, bf.position())
        }

        //google way
        this.toCommandLine().addArgument(googleFile).let {
            log.info(it.toString())
            DefaultExecutor().execute(it)
        }

        Helper.assertFileEquals(clearFile, googleFile)
        return this
    }

    fun sign(): VendorBoot {
        val avbtool = String.format(Helper.prop("avbtool")!!, "v1.2")
        File(Avb.getJsonFileName(info.role)).let {
            if (it.exists()) {
                //Signer.signAVB(info.role, this.info.imageSize, avbtool)
                val clearFile = Helper.joinPath(Helper.prop("intermediateDir")!!, this.info.role + ".clear")
                val signedFile = Helper.joinPath(Helper.prop("intermediateDir")!!, this.info.role + ".signed")
                Signer.signAVB2(clearFile,
                    signedFile,
                    Avb.getJsonFileName(info.role),
                    this.info.imageSize,
                    avbtool
                )
            } else {
                log.warn("skip signing of ${info.role}")
            }
        }
        return this
    }

    fun postCopy(outFile: String): VendorBoot {
        val dir = Helper.prop("intermediateDir")!!
        val signedFile = Helper.joinPath(dir, "${info.role}.signed").takeIf { File(it).exists() }
            ?: Helper.joinPath(dir, "${info.role}.clear")
        log.info("COPY $signedFile -> $outFile")
        File(signedFile).copyTo(File(outFile), overwrite = true)
        return this
    }

    fun updateVbmeta(): VendorBoot {
        Avb.updateVbmeta(info.role)
        return this
    }

    private fun toHeader(): VendorBootHeader {
        return VendorBootHeader(
            headerVersion = info.headerVersion,
            pageSize = info.pageSize,
            kernelLoadAddr = info.kernelLoadAddr,
            ramdiskLoadAddr = ramdisk.loadAddr,
            vndRamdiskTotalSize = ramdisk.size,
            cmdline = info.cmdline,
            tagsLoadAddr = info.tagsLoadAddr,
            product = info.product,
            headerSize = info.headerSize,
            dtbSize = dtb.size,
            dtbLoadAddr = dtb.loadAddr,
            vrtSize = ramdisk_table.eachEntrySize * ramdisk_table.ramdidks.size,
            vrtEntryNum = ramdisk_table.ramdidks.size,
            vrtEntrySize = ramdisk_table.eachEntrySize,
            bootconfigSize = File(bootconfig.file).length().toInt()
        ).feature67()
    }

    fun extractImages(): VendorBoot {
        //header
        ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(File(workDir, this.info.json), this)
        //ramdisk
        //@formatter:off
        val fmt = C.dumpRamdisk(
            Helper.Slice(info.input, ramdisk.position.toInt(), ramdisk.size, ramdisk.file), File(workDir, "root").path,
            this.ramdisk_table.ramdidks.isEmpty())
        //@formatter:on
        this.ramdisk.file = this.ramdisk.file + ".$fmt"
        if (fmt == "xz") {
            val checkType = ZipHelper.xzStreamFlagCheckTypeToString(ZipHelper.parseStreamFlagCheckType(this.ramdisk.file))
            this.ramdisk.xzFlags = checkType
        }
        //dtb
        run {
            C.dumpDtb(Helper.Slice(info.input, dtb.position.toInt(), dtb.size, dtb.file), false)
            if (dtb.size > 0) {
                dtb.dtbList = DTC.parseMultiple(dtb.file)
                DTC.extractMultiple(dtb.file, dtb.dtbList)
            }
        }
        //vrt
        this.ramdisk_table.ramdidks.forEachIndexed { index, it ->
            log.info("dumping vendor ramdisk ${index + 1}/${this.ramdisk_table.ramdidks.size} ...")
            val s = Helper.Slice(ramdisk.file, it.offset, it.size, it.file)
            C.dumpRamdisk(s, File(workDir, "root.${index + 1}").toString())
            it.file = it.file + ".$fmt"
        }
        //bootconfig
        if (bootconfig.size > 0) {
            Helper.Slice(info.input, bootconfig.position.toInt(), bootconfig.size, bootconfig.file).let { s ->
                Helper.extractFile(s.srcFile, s.dumpFile, s.offset.toLong(), s.length)
            }
        }
        //dump info again
        ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(File(File(workDir, this.info.json).toString()), this)
        return this
    }

    fun extractVBMeta(): VendorBoot {
        try {
            AVBInfo.parseFrom(Dumpling(info.input)).dumpDefault(info.role)
        } catch (e: Exception) {
            log.error("extraceVBMeta(): $e")
        }
        if (File("vbmeta.img").exists()) {
            log.warn("Found vbmeta.img, parsing ...")
            VBMetaParser().unpack("vbmeta.img")
        }
        return this
    }

    fun printUnpackSummary(): VendorBoot {
        val prints: MutableList<Pair<String, String>> = mutableListOf()
        val tableHeader = AsciiTable().apply {
            addRule()
            addRow("What", "Where")
            addRule()
        }
        val tab = AsciiTable().let {
            it.addRule()
            val imageInfoJsonFile = Helper.joinPath(workDir!!, info.role.removeSuffix(".img") + ".json").toShortenPath()
            it.addRow("image info", imageInfoJsonFile)
            prints.add(Pair("image info", imageInfoJsonFile))
            it.addRule()
            it.addRow("ramdisk", this.ramdisk.file.toShortenPath())
            prints.add(Pair("ramdisk", this.ramdisk.file.toShortenPath()))
            if (this.ramdisk_table.size > 0) {
                this.ramdisk_table.ramdidks.forEachIndexed { index, entry ->
                    //fancy ascii
                    it.addRow("-- ${entry.type} ramdisk[${index + 1}/${this.ramdisk_table.ramdidks.size}]", entry.file.toShortenPath())
                    it.addRow("------- extracted rootfs", File(workDir, "root.${index + 1}").path.toShortenPath())
                    //basic ascii
                    //@formatter:off
                    prints.add(Pair(" -- ${entry.type} ramdisk[${index + 1}/${this.ramdisk_table.ramdidks.size}]", entry.file.toShortenPath()))
                    //@formatter:on
                    prints.add(Pair(" ------- extracted rootfs", File(workDir, "root.${index + 1}").path.toShortenPath()))
                }
            } else {
                it.addRow("\\-- extracted ramdisk rootfs", File(workDir, "root").path.toShortenPath())
                prints.add(Pair("\\-- extracted ramdisk rootfs", File(workDir, "root").path.toShortenPath()))
            }
            it.addRule()
            if (this.dtb.size > 0) {
                it.addRow("dtb", this.dtb.file.toShortenPath())
                prints.add(Pair("dtb", this.dtb.file.toShortenPath()))
                if (File(this.dtb.file + ".0.${dtsSuffix}").exists()) {
                    it.addRow("\\-- decompiled dts [${dtb.dtbList.size}]", dtb.file + "*.${dtsSuffix}")
                    prints.add(Pair("\\-- decompiled dts [${dtb.dtbList.size}]", dtb.file + "*.${dtsSuffix}"))
                }
            } else {
                it.addRow("dtb", "-")
                prints.add(Pair("dtb", "-"))
            }
            if (this.bootconfig.size > 0) {
                it.addRule()
                it.addRow("bootconfig", this.bootconfig.file.toShortenPath())
                prints.add(Pair("bootconfig", this.bootconfig.file.toShortenPath()))
            }
            it.addRule()
            Avb.getJsonFileName(info.role).let { jsonFile ->
                if (File(jsonFile).exists()) {
                    it.addRow("AVB info", jsonFile.toShortenPath())
                    prints.add(Pair("AVB info", jsonFile.toShortenPath()))
                    mapper.readValue(File(jsonFile), AVBInfo::class.java).let { ai ->
                        it.addRow("\\-- signing key", Avb.inspectKey(ai))
                        prints.add(Pair(" \\-- signing key", Avb.inspectKey(ai)))
                    }
                } else {
                    it.addRow("AVB info", "none")
                    prints.add(Pair("AVB info", "none"))
                }
                it.addRule()
            }
            it
        }
        val tabVBMeta = AsciiTable().let {
            if (File("vbmeta.img").exists()) {
                it.addRule()
                it.addRow("vbmeta.img", Avb.getJsonFileName("vbmeta.img").toShortenPath())
                prints.add(Pair("vbmeta.img", Avb.getJsonFileName("vbmeta.img").toShortenPath()))
                it.addRule()
                "\n" + it.render()
            } else {
                ""
            }
        }
        if (environmentVerifier.isWindows) {
            log.info("\n" + Common.table2String(prints))
        } else {
            //@formatter:off
            log.info("\n\t\t\tUnpack Summary of ${info.role}\n{}\n{}{}", tableHeader.render(), tab.render(), tabVBMeta)
            //@formatter:on
        }
        return this
    }

    fun printPackSummary(outFileName: String): VendorBoot {
        Common.printPackSummary(info.role, outFileName)
        return this
    }

    private fun toCommandLine(): CommandLine {
        val cmdPrefix = if (environmentVerifier.isWindows) "python " else ""
        return CommandLine.parse(cmdPrefix + Helper.prop("mkbootimg")).apply {
            when (info.headerVersion) {
                3 -> {
                    addArgument("--vendor_ramdisk").addArgument(ramdisk.file)
                }
                else -> {
                    ramdisk_table.ramdidks.forEachIndexed { index, it ->
                        log.info("dumping vendor ramdisk ${index + 1}/${ramdisk_table.ramdidks.size} ...")
                        addArgument("--ramdisk_type").addArgument(it.type.toString())
                        Struct("${VrtEntry.VENDOR_RAMDISK_TABLE_ENTRY_BOARD_ID_SIZE}i").unpack(ByteArrayInputStream(it.boardId))
                            .forEachIndexed { boardIdIndex, boardIdValue ->
                                addArgument("--board_id$boardIdIndex")
                                addArgument("0x" + Integer.toHexString((boardIdValue as Int)))
                            }
                        if (environmentVerifier.isWindows) {
                            addArgument("--ramdisk_name").addArgument("\"${it.name}\"", false)
                        } else {
                            addArgument("--ramdisk_name").addArgument(it.name, true)
                        }
                        addArgument("--vendor_ramdisk_fragment").addArgument(it.file)
                    }
                    if (bootconfig.file.isNotBlank()) {
                        addArgument("--vendor_bootconfig").addArgument(bootconfig.file)
                    }
                }
            }
            if (info.product.isNotBlank()) {
                addArgument("--board").addArgument(info.product)
            }
            if (dtb.size > 0) {
                addArgument("--dtb").addArgument(dtb.file)
            }
            if (environmentVerifier.isWindows) {
                addArgument("--vendor_cmdline").addArgument(info.cmdline.replace("\"", "\\\""), false)
            } else {
                addArgument("--vendor_cmdline").addArgument(info.cmdline, false)
            }
            addArgument("--header_version").addArgument(info.headerVersion.toString())
            addArgument("--base").addArgument("0")
            addArgument("--tags_offset").addArgument(info.tagsLoadAddr.toString())
            addArgument("--kernel_offset").addArgument(info.kernelLoadAddr.toString())
            addArgument("--ramdisk_offset").addArgument(ramdisk.loadAddr.toString())
            addArgument("--dtb_offset").addArgument(dtb.loadAddr.toString())
            addArgument("--pagesize").addArgument(info.pageSize.toString())
            addArgument("--vendor_boot")
        }
    }
}
