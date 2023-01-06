package utils

import avb.AVBInfo
import cc.cfig.io.Struct
import cfig.Avb
import cfig.bootimg.Common
import cfig.bootimg.Signer
import cfig.bootimg.v3.VendorBoot
import cfig.helper.Helper
import cfig.helper.Dumpling
import cfig.packable.VBMetaParser
import cfig.utils.DTC
import com.fasterxml.jackson.databind.ObjectMapper
import de.vandermeer.asciitable.AsciiTable
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

class Dtbo(
    var info: DtboInfo = DtboInfo(),
    var header: DtboHeader = DtboHeader(),
    var dtEntries: MutableList<DeviceTreeTableEntry> = mutableListOf()
) {
    class DtboInfo(
        var output: String = "",
        var json: String = "",
        var imageSize: Int = 0
    )

    // part I: header
    data class DtboHeader(
        var totalSize: Int = 0,
        var headerSize: Int = 0,
        var entrySize: Int = 0,
        var entryCount: Int = 0,
        var entryOffset: Int = 0,
        var pageSize: Int = 0,
        var version: Int = 0
    ) {
        companion object {
            const val magic = 0xd7b7ab1e
            private const val FORMAT_STRING = ">I7i"
            internal const val SIZE = 32

            init {
                check(Struct(FORMAT_STRING).calcSize() == SIZE)
            }
        }

        constructor(iS: InputStream?) : this() {
            if (iS == null) {
                return
            }
            val info = Struct(FORMAT_STRING).unpack(iS)
            check(8 == info.size)
            if ((info[0] as UInt).toLong() != magic) {
                throw IllegalArgumentException("stream doesn't look like DTBO header")
            }
            totalSize = info[1] as Int
            headerSize = info[2] as Int
            if (headerSize != DtboHeader.SIZE) {
                log.warn("headerSize $headerSize != ${DtboHeader.SIZE}")
            }
            entrySize = info[3] as Int
            if (entrySize != DeviceTreeTableEntry.SIZE) {
                log.warn("entrySize $entrySize != ${DeviceTreeTableEntry.SIZE}")
            }
            entryCount = info[4] as Int
            entryOffset = info[5] as Int
            pageSize = info[6] as Int
            version = info[7] as Int
        }

        fun encode(): ByteArray {
            return Struct(FORMAT_STRING).pack(
                magic,
                totalSize,
                headerSize,
                entrySize,
                entryCount,
                entryOffset,
                pageSize,
                version
            )
        }
    }

    // part II: dt entry table
    data class DeviceTreeTableEntry(
        var sequenceNo: Int = 0,
        var entrySize: Int = 0,
        var entryOffset: Int = 0,
        var id: Int = 0,
        var rev: Int = 0,
        var flags: Int = 0,
        var reserved1: Int = 0,
        var reserved2: Int = 0,
        var reserved3: Int = 0,
    ) {
        companion object {
            private const val FORMAT_STRING = ">8i"
            internal const val SIZE = 32

            init {
                check(Struct(FORMAT_STRING).calcSize() == SIZE)
            }
        }

        constructor(iS: InputStream) : this() {
            val info = Struct(FORMAT_STRING).unpack(iS)
            check(8 == info.size)
            entrySize = info[0] as Int
            entryOffset = info[1] as Int
            id = info[2] as Int
            rev = info[3] as Int
            flags = info[4] as Int
            reserved1 = info[5] as Int
            reserved2 = info[6] as Int
            reserved3 = info[7] as Int
        }

        fun encode(): ByteArray {
            return Struct(FORMAT_STRING).pack(
                entrySize,
                entryOffset,
                id,
                rev,
                flags,
                reserved1,
                reserved2,
                reserved3
            )
        }
    }

    companion object {
        fun parse(fileName: String): Dtbo {
            val ret = Dtbo()
            ret.info.output = fileName
            ret.info.imageSize = File(fileName).length().toInt()
            ret.info.json = fileName.removeSuffix(".img") + ".json"
            FileInputStream(fileName).use { fis ->
                ret.header = DtboHeader(fis)
                for (i in 0 until ret.header.entryCount) {
                    ret.dtEntries.add(DeviceTreeTableEntry(fis).apply { sequenceNo = i })
                }
            }
            return ret
        }

        private val log = LoggerFactory.getLogger(Dtbo::class.java)
        private val outDir = Helper.prop("workDir")
        private val dtsSuffix = Helper.prop("config.dts_suffix")
    }

    fun extractVBMeta(): Dtbo {
        try {
            AVBInfo.parseFrom(Dumpling(info.output)).dumpDefault(info.output)
        } catch (e: Exception) {
            log.error("extraceVBMeta(): $e")
        }
        if (File("vbmeta.img").exists()) {
            log.warn("Found vbmeta.img, parsing ...")
            VBMetaParser().unpack("vbmeta.img")
        }
        return this
    }

    fun unpack(outDir: String): Dtbo {
        File("${outDir}dt").mkdir()
        ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(File("${outDir}dtbo.json"), this)
        dtEntries.forEach {
            Common.dumpDtb(Helper.Slice(info.output, it.entryOffset, it.entrySize, "${outDir}dt/dt.${it.sequenceNo}"))
        }
        return this
    }

    fun pack(): Dtbo {
        FileOutputStream(info.output + ".clear").use { fos ->
            // Part I
            this.header.entryCount = this.dtEntries.size
            this.header.totalSize = (DtboHeader.SIZE
                    + (header.entryCount * DeviceTreeTableEntry.SIZE)
                    + this.dtEntries.sumOf { File("${outDir}dt/dt.${it.sequenceNo}").length() })
                .toInt()
            // Part II - a
            for (index in 0 until dtEntries.size) {
                DTC().compile("${outDir}dt/dt.${index}.${dtsSuffix}", "${outDir}dt/dt.${index}")
            }
            // Part II - b
            var offset = DtboHeader.SIZE + (header.entryCount * DeviceTreeTableEntry.SIZE)
            this.dtEntries.forEachIndexed { index, deviceTreeTableEntry ->
                deviceTreeTableEntry.entrySize = File("${outDir}dt/dt.${index}").length().toInt()
                deviceTreeTableEntry.entryOffset = offset
                offset += deviceTreeTableEntry.entrySize
            }

            // + Part I
            fos.write(header.encode())
            // + Part II
            this.dtEntries.forEach {
                fos.write(it.encode())
            }
            // + Part III
            for (index in 0 until dtEntries.size) {
                fos.write(File("${outDir}dt/dt.${index}").readBytes())
            }
        }
        return this
    }

    fun printSummary(): Dtbo {
        val tableHeader = AsciiTable().apply {
            addRule()
            addRow("What", "Where")
            addRule()
        }
        val tab = AsciiTable().let {
            it.addRule()
            it.addRow("image info", outDir + info.output.removeSuffix(".img") + ".json")
            it.addRule()
            it.addRow("device-tree blob   (${this.header.entryCount} blobs)", "${outDir}dt/dt.*")
            it.addRow("\\-- device-tree source ", "${outDir}dt/dt.*.${dtsSuffix}")
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
        log.info("\n\t\t\tUnpack Summary of ${info.output}\n{}\n{}{}", tableHeader.render(), tab.render(), tabVBMeta)
        return this
    }

    fun sign(): Dtbo {
        val avbtool = String.format(Helper.prop("avbtool"), "v1.2")
        Signer.signAVB(info.output, info.imageSize.toLong(), avbtool)
        return this
    }

    fun updateVbmeta(): Dtbo {
        Avb.updateVbmeta(info.output)
        return this
    }

    fun printPackSummary(): Dtbo {
        VendorBoot.printPackSummary(info.output)
        return this
    }
}
