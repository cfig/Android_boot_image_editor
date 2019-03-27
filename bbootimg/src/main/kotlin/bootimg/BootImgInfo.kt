package cfig.bootimg

import cfig.ParamConfig
import org.apache.commons.exec.CommandLine
import java.io.InputStream

class BootImgInfo(iS: InputStream?) : BootImgHeader(iS) {
    constructor() : this(null)

    val kernelPosition: Int
        get() {
            return getHeaderSize(this.pageSize)
        }

    val ramdiskPosition: Int
        get() {
            return (kernelPosition + this.kernelLength +
                    getPaddingSize(this.kernelLength.toInt(), this.pageSize)).toInt()
        }

    val secondBootloaderPosition: Int
        get() {
            return (ramdiskPosition + ramdiskLength +
                    getPaddingSize(ramdiskLength.toInt(), pageSize)).toInt()
        }

    val recoveryDtboPosition: Int
        get() {
            return (secondBootloaderPosition + secondBootloaderLength +
                    getPaddingSize(secondBootloaderLength.toInt(), pageSize)).toInt()
        }

    val dtbPosition: Int
        get() {
            return (recoveryDtboPosition + dtbLength +
                    getPaddingSize(dtbLength.toInt(), pageSize)).toInt()
        }

    var signatureType: BootImgInfo.VerifyType? = null

    var imageSize: Long = 0

    private fun getHeaderSize(pageSize: Int): Int {
        val pad = (pageSize - (1648 and (pageSize - 1))) and (pageSize - 1)
        return pad + 1648
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
        ret.addArgument("0x" + java.lang.Long.toHexString(kernelOffset))
        if (this.ramdiskLength > 0) {
            ret.addArgument(" --ramdisk ")
            ret.addArgument(param.ramdisk)
        }
        ret.addArgument(" --ramdisk_offset ")
        ret.addArgument("0x" + java.lang.Long.toHexString(ramdiskOffset))
        if (this.secondBootloaderLength > 0) {
            ret.addArgument(" --second ")
            ret.addArgument(param.second)
        }
        ret.addArgument(" --second_offset ")
        ret.addArgument("0x" + java.lang.Long.toHexString(this.secondBootloaderOffset))
        if (!board.isBlank()) {
            ret.addArgument(" --board ")
            ret.addArgument(board)
        }
        if (headerVersion > 0) {
            if (this.recoveryDtboLength > 0) {
                ret.addArgument(" --recovery_dtbo ")
                ret.addArgument(param.dtbo)
            }
        }
        if (headerVersion > 1) {
            if (this.dtbLength > 0) {
                ret.addArgument("--dtb ")
                ret.addArgument(param.dtb)
            }
            ret.addArgument("--dtb_offset ")
            ret.addArgument("0x" + java.lang.Long.toHexString(this.dtbOffset))
        }
        ret.addArgument(" --pagesize ")
        ret.addArgument(Integer.toString(pageSize))
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
        ret.addArgument("0x" + java.lang.Long.toHexString(tagsOffset))
        ret.addArgument(" --id ")
        ret.addArgument(" --output ")
        //ret.addArgument("boot.img" + ".google")

        log.info("To Commandline: " + ret.toString())

        return ret
    }

    enum class VerifyType {
        VERIFY,
        AVB
    }
}
