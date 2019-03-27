package cfig

import cfig.bootimg.BootImgInfo
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import java.io.File

@JsonInclude(JsonInclude.Include.NON_NULL)
data class UnifiedConfig(
        var info: MiscInfo = MiscInfo(),
        var kernel: CommArgs = CommArgs(),
        var ramdisk: CommArgs? = null,
        var secondBootloader: CommArgs? = null,
        var recoveryDtbo: CommArgs? = null,
        var dtb: CommArgs? = null,
        var signature: Any? = null
) {
    data class CommArgs(
            var file: String? = null,
            var position: String = "0",
            var size: String = "0",
            var loadOffset: String = "0")

    data class MiscInfo(
            var output: String = "",
            var headerVersion: Int = 0,
            var headerSize: Int = 0,
            var loadBase: String = "",
            var tagsOffset: String = "0",
            var board: String? = null,
            var pageSize: Int = 0,
            var cmdline: String = "",
            var osVersion: String? = null,
            var osPatchLevel: String? = null,
            var hash: ByteArray = byteArrayOf(),
            var verify: BootImgInfo.VerifyType = BootImgInfo.VerifyType.VERIFY,
            var imageSize: Long = 0)

    fun toBootImgInfo(): BootImgInfo {
        val ret = BootImgInfo()
        ret.kernelOffset = this.kernel.loadOffset.removePrefix("0x").toLong(16)
        ret.kernelLength = Integer.decode(this.kernel.size).toLong()

        ret.kernelOffset = this.kernel.loadOffset.removePrefix("0x").toLong(16)
        ret.kernelLength = Integer.decode(this.kernel.size).toLong()

        this.ramdisk?.let {
            ret.ramdiskOffset = it.loadOffset.removePrefix("0x").toLong(16)
            ret.ramdiskLength = it.size.removePrefix("0x").toLong(16)
        }

        this.secondBootloader?.let {
            ret.secondBootloaderOffset = it.loadOffset.removePrefix("0x").toLong(16)
            ret.secondBootloaderLength = it.size.removePrefix("0x").toLong(16)
        }

        this.recoveryDtbo?.let {
            ret.recoveryDtboOffset = it.loadOffset.removePrefix("0x").toLong(16)
            ret.recoveryDtboLength = it.size.removePrefix("0x").toLong(16)
        }

        this.dtb?.let {
            ret.dtbOffset = it.loadOffset.removePrefix("0x").toLong(16)
            ret.dtbLength = it.size.removePrefix("0x").toLong(16)
        }

        ret.headerSize = this.info.headerSize.toLong()
        ret.headerVersion = this.info.headerVersion
        this.info.board?.let { ret.board = it }
        ret.tagsOffset = this.info.tagsOffset.removePrefix("0x").toLong(16)
        ret.cmdline = this.info.cmdline
        ret.osVersion = this.info.osVersion
        ret.osPatchLevel = this.info.osPatchLevel
        ret.hash = this.info.hash
        ret.pageSize = this.info.pageSize
        ret.signatureType = this.info.verify
        ret.imageSize = this.info.imageSize

        return ret
    }

    companion object {
        const val workDir = "build/unzip_boot/"
        private val log = LoggerFactory.getLogger(UnifiedConfig::class.java)

        fun fromBootImgInfo(info: BootImgInfo): UnifiedConfig {
            val ret = UnifiedConfig()
            val param = ParamConfig()
            ret.kernel.file = param.kernel
            ret.kernel.loadOffset = "0x${java.lang.Long.toHexString(info.kernelOffset)}"
            ret.kernel.size = "0x${Integer.toHexString(info.kernelLength.toInt())}"
            ret.kernel.position = "0x${Integer.toHexString(info.kernelPosition)}"

            ret.ramdisk = CommArgs()
            ret.ramdisk!!.loadOffset = "0x${java.lang.Long.toHexString(info.ramdiskOffset)}"
            ret.ramdisk!!.size = "0x${Integer.toHexString(info.ramdiskLength.toInt())}"
            ret.ramdisk!!.position = "0x${Integer.toHexString(info.ramdiskPosition)}"
            if (info.ramdiskLength > 0) {
                ret.ramdisk!!.file = param.ramdisk
            }

            ret.secondBootloader = CommArgs()
            ret.secondBootloader!!.loadOffset = "0x${java.lang.Long.toHexString(info.secondBootloaderOffset)}"
            ret.secondBootloader!!.size = "0x${Integer.toHexString(info.secondBootloaderLength.toInt())}"
            ret.secondBootloader!!.position = "0x${Integer.toHexString(info.secondBootloaderPosition)}"
            if (info.secondBootloaderLength > 0) {
                ret.secondBootloader!!.file = param.second
            }

            if (info.headerVersion > 0) {
                ret.recoveryDtbo = CommArgs()
                if (info.recoveryDtboLength > 0) {
                    ret.recoveryDtbo!!.file = param.dtbo
                }
                ret.recoveryDtbo!!.loadOffset = "0x${java.lang.Long.toHexString(info.recoveryDtboOffset)}"
                ret.recoveryDtbo!!.size = "0x${Integer.toHexString(info.recoveryDtboLength.toInt())}"
                ret.recoveryDtbo!!.position = "0x${Integer.toHexString(info.recoveryDtboPosition)}"
            }

            if (info.headerVersion > 1) {
                ret.dtb = CommArgs()
                if (info.dtbLength > 0) {
                    ret.dtb!!.file = param.dtb
                }
                ret.dtb!!.loadOffset = "0x${java.lang.Long.toHexString(info.dtbOffset)}"
                ret.dtb!!.size = "0x${Integer.toHexString(info.dtbLength.toInt())}"
                ret.dtb!!.position = "0x${Integer.toHexString(info.dtbPosition)}"
            }

            //ret.info.output = //unknown
            ret.info.headerSize = info.headerSize.toInt()
            ret.info.headerVersion = info.headerVersion
            ret.info.loadBase = "0x${java.lang.Long.toHexString(0)}"
            ret.info.board = if (info.board.isBlank()) null else info.board
            ret.info.tagsOffset = "0x${java.lang.Long.toHexString(info.tagsOffset)}"
            ret.info.cmdline = info.cmdline
            ret.info.osVersion = info.osVersion
            ret.info.osPatchLevel = info.osPatchLevel
            ret.info.hash = info.hash!!
            ret.info.pageSize = info.pageSize
            ret.info.verify = info.signatureType!!
            ret.info.imageSize = info.imageSize
            return ret
        }

        fun readBack2(): BootImgInfo {
            val param = ParamConfig()
            return ObjectMapper().readValue(File(param.cfg),
                    UnifiedConfig::class.java).toBootImgInfo()
        }
    }
}
