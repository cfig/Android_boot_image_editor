package cfig

import com.fasterxml.jackson.annotation.JsonInclude
import org.slf4j.LoggerFactory

@JsonInclude(JsonInclude.Include.NON_NULL)
data class UnifiedConfig(
        var info: MiscInfo = MiscInfo(),
        var kernel: CommArgs = CommArgs(),
        var ramdisk: CommArgs? = null,
        var secondBootloader: CommArgs? = null,
        var recoveryDtbo: CommArgs? = null,
        var signature: Any? = ImgInfo.VeritySignature()
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
            var hash: String = "",
            var verify: ImgArgs.VerifyType = ImgArgs.VerifyType.VERIFY)

    fun toArgs(): Array<Any> {
        val args = ImgArgs()
        val info = ImgInfo()

        args.output = this.info.output
        args.kernel = this.kernel.file ?: workDir + "kernel"
        args.kernelOffset = this.kernel.loadOffset.removePrefix("0x").toLong(16)
        info.kernelPosition = Integer.decode(this.kernel.position)
        info.kernelLength = Integer.decode(this.kernel.size)

        if (this.ramdisk == null) {
            args.ramdisk = null
        } else {
            args.ramdisk = this.ramdisk!!.file
            args.ramdiskOffset = this.ramdisk!!.loadOffset.removePrefix("0x").toLong(16)
            info.ramdiskPosition = Integer.decode(this.ramdisk!!.position)
            info.ramdiskLength = Integer.decode(this.ramdisk!!.size)
        }

        this.secondBootloader?.let {
            args.second = it.file
            args.secondOffset = it.loadOffset.removePrefix("0x").toLong(16)
            info.secondBootloaderPosition = Integer.decode(it.position)
            info.secondBootloaderLength = Integer.decode(it.size)
        }
        if (this.secondBootloader == null) args.second = null

        this.recoveryDtbo?.let {
            args.dtbo = it.file
            args.dtboOffset = it.loadOffset.removePrefix("0x").toLong(16)
            info.recoveryDtboPosition = Integer.decode(it.position)
            info.recoveryDtboLength = Integer.decode(it.size)
        }
        if (this.recoveryDtbo == null) args.dtbo = null

        info.headerSize = this.info.headerSize
        args.headerVersion = this.info.headerVersion
        args.base = this.info.loadBase.removePrefix("0x").toLong(16)
        this.info.board?.let { args.board = it }
        args.tagsOffset = this.info.tagsOffset.removePrefix("0x").toLong(16)
        args.cmdline = this.info.cmdline
        args.osVersion = this.info.osVersion
        args.osPatchLevel = this.info.osPatchLevel
        info.hash = Helper.fromHexString(this.info.hash)
        args.pageSize = this.info.pageSize
        args.verifyType = this.info.verify
        info.signature = this.signature

        return arrayOf(args, info)
    }

    companion object {
        const val workDir = "build/unzip_boot/"
        private val log = LoggerFactory.getLogger(UnifiedConfig::class.java)
        fun fromArgs(args: ImgArgs, info: ImgInfo): UnifiedConfig {
            val ret = UnifiedConfig()
            ret.kernel.file = args.kernel
            ret.kernel.loadOffset = "0x${java.lang.Long.toHexString(args.kernelOffset)}"
            ret.kernel.size = "0x${Integer.toHexString(info.kernelLength)}"
            ret.kernel.position = "0x${Integer.toHexString(info.kernelPosition)}"

            ret.ramdisk = CommArgs()
            ret.ramdisk!!.loadOffset = "0x${java.lang.Long.toHexString(args.ramdiskOffset)}"
            ret.ramdisk!!.size = "0x${Integer.toHexString(info.ramdiskLength)}"
            ret.ramdisk!!.position = "0x${Integer.toHexString(info.ramdiskPosition)}"
            args.ramdisk?.let {
                ret.ramdisk!!.file = args.ramdisk
            }

            ret.secondBootloader = CommArgs()
            ret.secondBootloader!!.loadOffset = "0x${java.lang.Long.toHexString(args.secondOffset)}"
            ret.secondBootloader!!.size = "0x${Integer.toHexString(info.secondBootloaderLength)}"
            ret.secondBootloader!!.position = "0x${Integer.toHexString(info.secondBootloaderPosition)}"
            args.second?.let {
                ret.secondBootloader!!.file = args.second
            }

            if (args.headerVersion > 0) {
                ret.recoveryDtbo = CommArgs()
                args.dtbo?.let {
                    ret.recoveryDtbo!!.file = args.dtbo
                }
                ret.recoveryDtbo!!.loadOffset = "0x${java.lang.Long.toHexString(args.dtboOffset)}"
                ret.recoveryDtbo!!.size = "0x${Integer.toHexString(info.recoveryDtboLength)}"
                ret.recoveryDtbo!!.position = "0x${Integer.toHexString(info.recoveryDtboPosition)}"
            }

            ret.info.output = args.output
            ret.info.headerSize = info.headerSize
            ret.info.headerVersion = args.headerVersion
            ret.info.loadBase = "0x${java.lang.Long.toHexString(args.base)}"
            ret.info.board = if (args.board.isBlank()) null else args.board
            ret.info.tagsOffset = "0x${java.lang.Long.toHexString(args.tagsOffset)}"
            ret.info.cmdline = args.cmdline
            ret.info.osVersion = args.osVersion
            ret.info.osPatchLevel = args.osPatchLevel
            ret.info.hash = Helper.toHexString(info.hash)
            ret.info.pageSize = args.pageSize

            ret.info.verify = args.verifyType
            ret.signature = info.signature

            return ret
        }
    }
}
