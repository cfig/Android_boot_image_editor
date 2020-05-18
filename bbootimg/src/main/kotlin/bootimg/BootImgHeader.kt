package cfig.bootimg

import cfig.Helper
import cfig.ParamConfig
import cfig.io.Struct3
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.regex.Pattern
import kotlin.math.pow

@OptIn(ExperimentalUnsignedTypes::class)
open class BootImgHeader(
        var kernelLength: UInt = 0U,
        var kernelOffset: UInt = 0U,

        var ramdiskLength: UInt = 0U,
        var ramdiskOffset: UInt = 0U,

        var secondBootloaderLength: UInt = 0U,
        var secondBootloaderOffset: UInt = 0U,

        var recoveryDtboLength: UInt = 0U,
        var recoveryDtboOffset: ULong = 0UL,//Q

        var dtbLength: UInt = 0U,
        var dtbOffset: ULong = 0UL,//Q

        var tagsOffset: UInt = 0U,

        var pageSize: UInt = 0U,

        var headerSize: UInt = 0U,
        var headerVersion: UInt = 0U,

        var board: String = "",

        var cmdline: String = "",

        var hash: ByteArray? = null,

        var osVersion: String? = null,
        var osPatchLevel: String? = null) {
    @Throws(IllegalArgumentException::class)
    constructor(iS: InputStream?) : this() {
        if (iS == null) {
            return
        }
        log.warn("BootImgHeader constructor")
        val info = Struct3(FORMAT_STRING).unpack(iS)
        assert(20 == info.size)
        if (info[0] != magic) {
            throw IllegalArgumentException("stream doesn't look like Android Boot Image Header")
        }
        this.kernelLength = info[1] as UInt
        this.kernelOffset = info[2] as UInt
        this.ramdiskLength = info[3] as UInt
        this.ramdiskOffset = info[4] as UInt
        this.secondBootloaderLength = info[5] as UInt
        this.secondBootloaderOffset = info[6] as UInt
        this.tagsOffset = info[7] as UInt
        this.pageSize = info[8] as UInt
        this.headerVersion = info[9] as UInt
        val osNPatch = info[10] as UInt
        if (0U != osNPatch) { //treated as 'reserved' in this boot image
            this.osVersion = parseOsVersion(osNPatch.toInt() shr 11)
            this.osPatchLevel = parseOsPatchLevel((osNPatch and 0x7ff.toUInt()).toInt())
        }
        this.board = info[11] as String
        this.cmdline = (info[12] as String) + (info[14] as String)
        this.hash = info[13] as ByteArray

        if (this.headerVersion > 0U) {
            this.recoveryDtboLength = info[15] as UInt
            this.recoveryDtboOffset = info[16] as ULong
        }

        this.headerSize = info[17] as UInt
        assert(this.headerSize.toInt() in intArrayOf(BOOT_IMAGE_HEADER_V2_SIZE, BOOT_IMAGE_HEADER_V1_SIZE))

        if (this.headerVersion > 1U) {
            this.dtbLength = info[18] as UInt
            this.dtbOffset = info[19] as ULong
        }
    }

    private fun parseOsVersion(x: Int): String {
        val a = x shr 14
        val b = x - (a shl 14) shr 7
        val c = x and 0x7f
        return String.format("%d.%d.%d", a, b, c)
    }

    private fun parseOsPatchLevel(x: Int): String {
        var y = x shr 4
        val m = x and 0xf
        y += 2000
        return String.format("%d-%02d-%02d", y, m, 0)
    }

    @Throws(IllegalArgumentException::class)
    private fun packOsVersion(x: String?): Int {
        if (x.isNullOrBlank()) return 0
        val pattern = Pattern.compile("^(\\d{1,3})(?:\\.(\\d{1,3})(?:\\.(\\d{1,3}))?)?")
        val m = pattern.matcher(x)
        if (m.find()) {
            val a = Integer.decode(m.group(1))
            var b = 0
            var c = 0
            if (m.groupCount() >= 2) {
                b = Integer.decode(m.group(2))
            }
            if (m.groupCount() == 3) {
                c = Integer.decode(m.group(3))
            }
            assert(a < 128)
            assert(b < 128)
            assert(c < 128)
            return (a shl 14) or (b shl 7) or c
        } else {
            throw IllegalArgumentException("invalid os_version")
        }
    }

    private fun packOsPatchLevel(x: String?): Int {
        if (x.isNullOrBlank()) return 0
        val ret: Int
        val pattern = Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})")
        val matcher = pattern.matcher(x)
        if (matcher.find()) {
            val y = Integer.parseInt(matcher.group(1), 10) - 2000
            val m = Integer.parseInt(matcher.group(2), 10)
            // 7 bits allocated for the year, 4 bits for the month
            assert(y in 0..127)
            assert(m in 1..12)
            ret = (y shl 4) or m
        } else {
            throw IllegalArgumentException("invalid os_patch_level")
        }

        return ret
    }

    @Throws(CloneNotSupportedException::class)
    private fun hashFileAndSize(vararg inFiles: String?): ByteArray {
        val md = MessageDigest.getInstance("SHA1")
        for (item in inFiles) {
            if (null == item) {
                md.update(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                        .putInt(0)
                        .array())
                log.debug("update null $item: " + Helper.toHexString((md.clone() as MessageDigest).digest()))
            } else {
                val currentFile = File(item)
                FileInputStream(currentFile).use { iS ->
                    var byteRead: Int
                    val dataRead = ByteArray(1024)
                    while (true) {
                        byteRead = iS.read(dataRead)
                        if (-1 == byteRead) {
                            break
                        }
                        md.update(dataRead, 0, byteRead)
                    }
                    log.debug("update file $item: " + Helper.toHexString((md.clone() as MessageDigest).digest()))
                    md.update(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                            .putInt(currentFile.length().toInt())
                            .array())
                    log.debug("update SIZE $item: " + Helper.toHexString((md.clone() as MessageDigest).digest()))
                }
            }
        }

        return md.digest()
    }

    private fun get_recovery_dtbo_offset(): UInt {
        return Helper.round_to_multiple(this.headerSize, pageSize) +
                Helper.round_to_multiple(this.kernelLength, pageSize) +
                Helper.round_to_multiple(this.ramdiskLength, pageSize) +
                Helper.round_to_multiple(this.secondBootloaderLength, pageSize)
    }

    private fun refresh() {
        val param = ParamConfig()
        //refresh kernel size
        if (0U == this.kernelLength) {
            throw java.lang.IllegalArgumentException("kernel size can not be 0")
        } else {
            this.kernelLength = File(param.kernel).length().toUInt()
        }
        //refresh ramdisk size
        if (0U == this.ramdiskLength) {
            param.ramdisk = null
            this.ramdiskOffset = 0U
        } else {
            this.ramdiskLength = File(param.ramdisk!!).length().toUInt()
        }
        //refresh second bootloader size
        if (0U == this.secondBootloaderLength) {
            param.second = null
            this.secondBootloaderOffset = 0U
        } else {
            this.secondBootloaderLength = File(param.second!!).length().toUInt()
        }
        //refresh recovery dtbo size
        if (0U == this.recoveryDtboLength) {
            param.dtbo = null
            this.recoveryDtboOffset = 0U
        } else {
            this.recoveryDtboLength = File(param.dtbo!!).length().toUInt()
            this.recoveryDtboOffset = get_recovery_dtbo_offset().toULong()
            log.warn("using fake recoveryDtboOffset $recoveryDtboOffset (as is in AOSP avbtool)")
        }
        //refresh dtb size
        if (0U == this.dtbLength) {
            param.dtb = null
        } else {
            this.dtbLength = File(param.dtb!!).length().toUInt()
        }
        //refresh image hash
        val imageId = when (this.headerVersion) {
            0U -> {
                hashFileAndSize(param.kernel, param.ramdisk, param.second)
            }
            1U -> {
                hashFileAndSize(param.kernel, param.ramdisk, param.second, param.dtbo)
            }
            2U -> {
                hashFileAndSize(param.kernel, param.ramdisk, param.second, param.dtbo, param.dtb)
            }
            else -> {
                throw java.lang.IllegalArgumentException("headerVersion ${this.headerVersion} illegal")
            }
        }
        this.hash = imageId
    }

    fun encode(): ByteArray {
        this.refresh()
        val pageSizeChoices: MutableSet<Long> = mutableSetOf<Long>().apply {
            (11..14).forEach { add(2.0.pow(it).toLong()) }
        }
        assert(pageSizeChoices.contains(pageSize.toLong())) { "invalid parameter [pageSize=$pageSize], (choose from $pageSizeChoices)" }
        return Struct3(FORMAT_STRING).pack(
                "ANDROID!",
                //10I
                kernelLength,
                kernelOffset,
                ramdiskLength,
                ramdiskOffset,
                secondBootloaderLength,
                secondBootloaderOffset,
                tagsOffset,
                pageSize,
                headerVersion,
                (packOsVersion(osVersion) shl 11) or packOsPatchLevel(osPatchLevel),
                //16s
                board,
                //512s
                cmdline.substring(0, minOf(512, cmdline.length)),
                //32b
                hash!!,
                //1024s
                if (cmdline.length > 512) cmdline.substring(512) else "",
                //I
                recoveryDtboLength,
                //Q
                if (headerVersion > 0U) recoveryDtboOffset else 0,
                //I
                when (headerVersion) {
                    0U -> 0
                    1U -> BOOT_IMAGE_HEADER_V1_SIZE
                    2U -> BOOT_IMAGE_HEADER_V2_SIZE
                    else -> java.lang.IllegalArgumentException("headerVersion $headerVersion illegal")
                },
                //I
                dtbLength,
                //Q
                if (headerVersion > 1U) dtbOffset else 0
        )
    }

    companion object {
        internal val log = LoggerFactory.getLogger(BootImgInfo::class.java)
        const val magic = "ANDROID!"
        const val FORMAT_STRING = "8s" + //"ANDROID!"
                "10I" +
                "16s" +     //board name
                "512s" +    //cmdline part 1
                "32b" +     //hash digest
                "1024s" +   //cmdline part 2
                "I" +       //dtbo length [v1]
                "Q" +       //dtbo offset [v1]
                "I" +       //header size [v1]
                "I" +       //dtb length [v2]
                "Q"         //dtb offset [v2]
        const val BOOT_IMAGE_HEADER_V2_SIZE = 1660
        const val BOOT_IMAGE_HEADER_V1_SIZE = 1648

        init {
            assert(BOOT_IMAGE_HEADER_V2_SIZE == Struct3(FORMAT_STRING).calcSize())
        }
    }
}
