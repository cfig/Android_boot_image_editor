package cfig.bootimg.v3

import cfig.bootimg.Common
import cfig.io.Struct3
import org.slf4j.LoggerFactory
import java.io.InputStream

@OptIn(ExperimentalUnsignedTypes::class)
class BootHeaderV3(
        var kernelSize: UInt = 0U,
        var ramdiskSize: UInt = 0U,
        var osVersion: String = "",
        var osPatchLevel: String = "",
        var headerSize: UInt = 0U,
        var headerVersion: UInt = 0U,
        var cmdline: String = ""
) {
    @Throws(IllegalArgumentException::class)
    constructor(iS: InputStream?) : this() {
        if (iS == null) {
            return
        }
        log.warn("BootImgHeaderV3 constructor")
        val info = Struct3(FORMAT_STRING).unpack(iS)
        assert(11 == info.size)
        if (info[0] != magic) {
            throw IllegalArgumentException("stream doesn't look like Android Boot Image V3 Header")
        }
        this.kernelSize = info[1] as UInt
        this.ramdiskSize = info[2] as UInt
        val osNPatch = info[3] as UInt
        if (0U != osNPatch) { //treated as 'reserved' in this boot image
            this.osVersion = Common.parseOsVersion(osNPatch.toInt() shr 11)
            this.osPatchLevel = Common.parseOsPatchLevel((osNPatch and 0x7ff.toUInt()).toInt())
        }
        this.headerSize = info[4] as UInt
        //5,6,7,8 reserved
        this.headerVersion = info[9] as UInt

        this.cmdline = info[10] as String

        assert(this.headerSize.toInt() in intArrayOf(BOOT_IMAGE_HEADER_V3_SIZE))
    }

    fun encode(): ByteArray {
        return Struct3(FORMAT_STRING).pack(
                magic,
                kernelSize,
                ramdiskSize,
                (Common.packOsVersion(osVersion) shl 11) or Common.packOsPatchLevel(osPatchLevel),
                headerSize,
                0,
                0,
                0,
                0,
                headerVersion,
                cmdline)
    }

    override fun toString(): String {
        return "BootImgHeaderV3(kernelSize=$kernelSize, ramdiskSize=$ramdiskSize, osVersion=$osVersion, osPatchLevel=$osPatchLevel, headerSize=$headerSize, headerVersion=$headerVersion, cmdline='$cmdline')"
    }

    companion object {
        internal val log = LoggerFactory.getLogger(BootHeaderV3::class.java)
        const val magic = "ANDROID!"
        const val FORMAT_STRING = "8s" + //"ANDROID!"
                "4I" + //kernel size, ramdisk size, os_version/patch, header size
                "4I" + //reserved
                "I" +  //header version
                "1536s"     //cmdline
        private const val BOOT_IMAGE_HEADER_V3_SIZE = 1580
        val pageSize: UInt = 4096U

        init {
            assert(BOOT_IMAGE_HEADER_V3_SIZE == Struct3(FORMAT_STRING).calcSize()) {
                "internal error: expected size $BOOT_IMAGE_HEADER_V3_SIZE "
            }
        }
    }
}
