package cfig.bootimg

import cfig.Helper
import cfig.ParamConfig
import cfig.io.Struct
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.Assert
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.regex.Pattern

open class BootImgHeader(
        var kernelLength: Long = 0,
        var kernelOffset: Long = 0,

        var ramdiskLength: Long = 0,
        var ramdiskOffset: Long = 0,

        var secondBootloaderLength: Long = 0,
        var secondBootloaderOffset: Long = 0,

        var recoveryDtboLength: Long = 0,
        var recoveryDtboOffset: Long = 0,

        var dtbLength: Long = 0,
        var dtbOffset: Long = 0,

        var tagsOffset: Long = 0,

        var pageSize: Int = 0,

        var headerSize: Long = 0,
        var headerVersion: Int = 0,

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
        val info = Struct(FORMAT_STRING).unpack(iS)
        Assert.assertEquals(20, info.size)
        if (!(info[0] as ByteArray).contentEquals(magic.toByteArray())) {
            throw IllegalArgumentException("stream doesn't look like Android Boot Image Header")
        }
        this.kernelLength = info[1] as Long
        this.kernelOffset = info[2] as Long
        this.ramdiskLength = info[3] as Long
        this.ramdiskOffset = info[4] as Long
        this.secondBootloaderLength = info[5] as Long
        this.secondBootloaderOffset = info[6] as Long
        this.tagsOffset = info[7] as Long
        this.pageSize = (info[8] as Long).toInt()
        this.headerVersion = (info[9] as Long).toInt()
        val osNPatch = (info[10] as Long).toInt()
        if (0 != osNPatch) { //treated as 'reserved' in this boot image
            this.osVersion = parseOsVersion(osNPatch shr 11)
            this.osPatchLevel = parseOsPatchLevel(osNPatch and 0x7ff)
        }
        this.board = Helper.toCString(info[11] as ByteArray).trim()
        this.cmdline = Helper.toCString(info[12] as ByteArray) + Helper.toCString(info[14] as ByteArray)
        this.hash = info[13] as ByteArray

        if (this.headerVersion > 0) {
            this.recoveryDtboLength = info[15] as Long
            this.recoveryDtboOffset = info[16] as Long
        }

        this.headerSize = info[17] as Long
        assert(this.headerSize.toInt() in intArrayOf(BOOT_IMAGE_HEADER_V2_SIZE, BOOT_IMAGE_HEADER_V1_SIZE))

        if (this.headerVersion > 1) {
            this.dtbLength = info[18] as Long
            this.dtbOffset = info[19] as Long
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
            Assert.assertTrue(a < 128)
            Assert.assertTrue(b < 128)
            Assert.assertTrue(c < 128)
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
            Assert.assertTrue(y in 0..127)
            Assert.assertTrue(m in 1..12)
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
                    var dataRead = ByteArray(1024)
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

    private fun refresh() {
        val param = ParamConfig()
        //refresh kernel size
        if (0L == this.kernelLength) {
            throw java.lang.IllegalArgumentException("kernel size can not be 0")
        } else {
            this.kernelLength = File(param.kernel).length()
        }
        //refresh ramdisk size
        if (0L == this.ramdiskLength) {
            param.ramdisk = null
        } else {
            this.ramdiskLength = File(param.ramdisk).length()
        }
        //refresh second bootloader size
        if (0L == this.secondBootloaderLength) {
            param.second = null
        } else {
            this.secondBootloaderLength = File(param.second).length()
        }
        //refresh recovery dtbo size
        if (0L == this.recoveryDtboLength) {
            param.dtbo = null
        } else {
            this.recoveryDtboLength = File(param.dtbo).length()
        }
        //refresh recovery dtbo size
        if (0L == this.dtbLength) {
            param.dtb = null
        } else {
            this.dtbLength = File(param.dtb).length()
        }

        //refresh image hash
        val imageId = when (this.headerVersion) {
            0 -> {
                hashFileAndSize(param.kernel, param.ramdisk, param.second)
            }
            1 -> {
                hashFileAndSize(param.kernel, param.ramdisk, param.second, param.dtbo)
            }
            2 -> {
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
        val ret = Struct(FORMAT_STRING).pack(
                "ANDROID!".toByteArray(),
                //10I
                this.kernelLength,
                this.kernelOffset,
                this.ramdiskLength,
                this.ramdiskOffset,
                this.secondBootloaderLength,
                this.secondBootloaderOffset,
                this.tagsOffset,
                this.pageSize,
                this.headerVersion,
                (packOsVersion(this.osVersion) shl 11) or packOsPatchLevel(this.osPatchLevel),
                //16s
                this.board.toByteArray(),
                //512s
                this.cmdline.substring(0, minOf(512, this.cmdline.length)).toByteArray(),
                //32s
                this.hash!!,
                //1024s
                if (this.cmdline.length > 512) this.cmdline.substring(512).toByteArray() else byteArrayOf(0),
                //I
                this.recoveryDtboLength,
                //Q
                if (this.headerVersion > 0) this.recoveryDtboOffset else 0,
                //I
                when (this.headerVersion) {
                    0 -> 0
                    1 -> BOOT_IMAGE_HEADER_V1_SIZE
                    2 -> BOOT_IMAGE_HEADER_V2_SIZE
                    else -> java.lang.IllegalArgumentException("headerVersion ${this.headerVersion} illegal")
                },
                //I
                this.dtbLength,
                //Q
                if (this.headerVersion > 1) this.dtbOffset else 0
        )
        return ret
    }

    companion object {
        internal val log = LoggerFactory.getLogger(BootImgInfo::class.java)
        const val magic = "ANDROID!"
        const val FORMAT_STRING = "8s" + //"ANDROID!"
                "10I" +
                "16s" +     //board name
                "512s" +    //cmdline part 1
                "32s" +     //hash digest
                "1024s" +   //cmdline part 2
                "I" +       //dtbo length [v1]
                "Q" +       //dtbo offset [v1]
                "I" +       //header size [v1]
                "I" +       //dtb length [v2]
                "Q"         //dtb offset [v2]
        const val BOOT_IMAGE_HEADER_V2_SIZE = 1660
        const val BOOT_IMAGE_HEADER_V1_SIZE = 1648

        init {
            Assert.assertEquals(BOOT_IMAGE_HEADER_V2_SIZE, Struct(FORMAT_STRING).calcSize())
        }
    }
}
