package cfig.bootimg

import cfig.ParamConfig
import org.apache.commons.exec.CommandLine
import java.io.InputStream

@ExperimentalUnsignedTypes
class BootImgInfo(iS: InputStream?) : BootImgHeader(iS) {
    constructor() : this(null)

    val kernelPosition: UInt
        get() {
            return getHeaderSize(this.pageSize)
        }

    val ramdiskPosition: UInt
        get() {
            return (kernelPosition + this.kernelLength +
                    getPaddingSize(this.kernelLength, this.pageSize))
        }

    val secondBootloaderPosition: UInt
        get() {
            return ramdiskPosition + ramdiskLength +
                    getPaddingSize(ramdiskLength, pageSize)
        }

    val recoveryDtboPosition: ULong
        get() {
            return secondBootloaderPosition.toULong() + secondBootloaderLength +
                    getPaddingSize(secondBootloaderLength, pageSize)
        }

    val dtbPosition: ULong
        get() {
            return recoveryDtboPosition + recoveryDtboLength +
                    getPaddingSize(recoveryDtboLength, pageSize)
        }

    var signatureType: BootImgInfo.VerifyType? = null

    var imageSize: Long = 0

    private fun getHeaderSize(pageSize: Int): Int {
        val pad = (pageSize - (1648 and (pageSize - 1))) and (pageSize - 1)
        return pad + 1648
    }

    private fun getHeaderSize(pageSize: UInt): UInt {
        val pad = (pageSize - (1648U and (pageSize - 1U))) and (pageSize - 1U)
        return pad + 1648U
    }

    private fun getPaddingSize(position: UInt, pageSize: UInt): UInt {
        return (pageSize - (position and pageSize - 1U)) and (pageSize - 1U)
    }

    private fun getPaddingSize(position: Int, pageSize: Int): Int {
        return (pageSize - (position and pageSize - 1)) and (pageSize - 1)
    }

    fun toCommandLine(): CommandLine {
        val param = ParamConfig()
        val ret = CommandLine(param.mkbootimg)
        ret.addArgument(" --header_version ")
        ret.addArgument(headerVersion.toString())
        ret.addArgument(" --base ")
        ret.addArgument("0x" + java.lang.Long.toHexString(0))
        ret.addArgument(" --kernel ")
        ret.addArgument(param.kernel)
        ret.addArgument(" --kernel_offset ")
        ret.addArgument("0x" + Integer.toHexString(kernelOffset.toInt()))
        if (this.ramdiskLength > 0U) {
            ret.addArgument(" --ramdisk ")
            ret.addArgument(param.ramdisk)
        }
        ret.addArgument(" --ramdisk_offset ")
        ret.addArgument("0x" + Integer.toHexString(ramdiskOffset.toInt()))
        if (this.secondBootloaderLength > 0U) {
            ret.addArgument(" --second ")
            ret.addArgument(param.second)
            ret.addArgument(" --second_offset ")
            ret.addArgument("0x" + Integer.toHexString(this.secondBootloaderOffset.toInt()))
        }
        if (!board.isBlank()) {
            ret.addArgument(" --board ")
            ret.addArgument(board)
        }
        if (headerVersion > 0U) {
            if (this.recoveryDtboLength > 0U) {
                ret.addArgument(" --recovery_dtbo ")
                ret.addArgument(param.dtbo)
            }
        }
        if (headerVersion > 1U) {
            if (this.dtbLength > 0U) {
                ret.addArgument("--dtb ")
                ret.addArgument(param.dtb)
            }
            ret.addArgument("--dtb_offset ")
            ret.addArgument("0x" + java.lang.Long.toHexString(this.dtbOffset.toLong()))
        }
        ret.addArgument(" --pagesize ")
        ret.addArgument(Integer.toString(pageSize.toInt()))
        ret.addArgument(" --cmdline ")
        ret.addArgument(cmdline, false)
        if (!osVersion.isNullOrBlank()) {
            ret.addArgument(" --os_version ")
            ret.addArgument(osVersion)
        }
        if (!osPatchLevel.isNullOrBlank()) {
            ret.addArgument(" --os_patch_level ")
            ret.addArgument(osPatchLevel)
        }
        ret.addArgument(" --tags_offset ")
        ret.addArgument("0x" + Integer.toHexString(tagsOffset.toInt()))
        ret.addArgument(" --id ")
        ret.addArgument(" --output ")
        //ret.addArgument("boot.img" + ".google")

        log.debug("To Commandline: " + ret.toString())

        return ret
    }

    enum class VerifyType {
        VERIFY,
        AVB
    }
}
