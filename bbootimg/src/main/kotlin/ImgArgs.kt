package cfig

import org.apache.commons.exec.CommandLine
import java.util.*

data class ImgArgs(
        //file input
        var kernel: String = UnifiedConfig.workDir + "kernel",
        var ramdisk: String? = UnifiedConfig.workDir + "ramdisk.img.gz",
        var second: String? = UnifiedConfig.workDir + "second",
        var dtbo: String? = UnifiedConfig.workDir + "dtbo",
        //file output
        var output: String = "boot.img",
        var cfg: String = UnifiedConfig.workDir + "bootimg.json",

        //load address
        internal var base: Long = 0,
        internal var kernelOffset: Long = 0,
        var ramdiskOffset: Long = 0,
        var secondOffset: Long = 0,
        var tagsOffset: Long = 0,
        var dtboOffset: Long = 0,

        var board: String = "",
        var cmdline: String = "",
        var osVersion: String? = null,
        var osPatchLevel: String? = null,
        var headerVersion: Int = 0,
        var pageSize: Int = 0,
        var id: Boolean = true,
        //internal
        var mkbootimg: String = "../src/mkbootimg/mkbootimg",

        //signature
        var verifyType: VerifyType = VerifyType.VERIFY
) {
    enum class VerifyType {
        VERIFY,
        AVB
    }

    fun toCommandList(): List<String> {
        val ret = ArrayList<String>()
        ret.add(mkbootimg)
        ret.add("--header_version")
        ret.add(headerVersion.toString())
        ret.add("--base")
        ret.add("0x" + java.lang.Long.toHexString(base))
        ret.add("--kernel")
        ret.add(kernel)
        ret.add("--kernel_offset")
        ret.add("0x" + java.lang.Long.toHexString(kernelOffset))
        ramdisk?.let {
            ret.add("--ramdisk")
            ret.add(it)
        }
        ret.add("--ramdisk_offset")
        ret.add("0x" + java.lang.Long.toHexString(ramdiskOffset))
        second?.let {
            ret.add("--second")
            ret.add(it)
        }
        ret.add("--second_offset")
        ret.add("0x" + java.lang.Long.toHexString(secondOffset))
        if (!board.isBlank()) {
            ret.add("--board")
            ret.add(board)
        }
        if (headerVersion > 0) {
            dtbo?.let { dtbo ->
                ret.add("--recovery_dtbo")
                ret.add(dtbo)
            }
            ret.add("--recovery_dtbo_offset")
            ret.add("0x" + java.lang.Long.toHexString(dtboOffset))
        }
        ret.add("--pagesize")
        ret.add(Integer.toString(pageSize))
        ret.add("--cmdline")
        ret.add(cmdline)
        if (!osVersion.isNullOrBlank()) {
            ret.add("--os_version")
            ret.add(osVersion!!)
        }
        if (!osPatchLevel.isNullOrBlank()) {
            ret.add("--os_patch_level")
            ret.add(osPatchLevel!!)
        }
        ret.add("--tags_offset")
        ret.add("0x" + java.lang.Long.toHexString(tagsOffset))
        if (id) {
            ret.add("--id")
        }
        ret.add("--output")
        ret.add(output + ".google")

        return ret
    }

    fun toCommandString(): String {
        val ret = StringBuilder()
        ret.append(mkbootimg)
        ret.append(" --header_version ")
        ret.append(headerVersion.toString())
        ret.append(" --base ")
        ret.append("0x" + java.lang.Long.toHexString(base))
        ret.append(" --kernel ")
        ret.append(kernel)
        ret.append(" --kernel_offset ")
        ret.append("0x" + java.lang.Long.toHexString(kernelOffset))
        ramdisk?.let {
            ret.append(" --ramdisk ")
            ret.append(it)
        }
        ret.append(" --ramdisk_offset ")
        ret.append("0x" + java.lang.Long.toHexString(ramdiskOffset))
        second?.let {
            ret.append(" --second ")
            ret.append(it)
        }
        ret.append(" --second_offset ")
        ret.append("0x" + java.lang.Long.toHexString(secondOffset))
        if (!board.isBlank()) {
            ret.append(" --board ")
            ret.append(board)
        }
        if (headerVersion > 0) {
            dtbo?.let { dtbo ->
                ret.append(" --recovery_dtbo ")
                ret.append(dtbo)
            }
            ret.append(" --recovery_dtbo_offset ")
            ret.append("0x" + java.lang.Long.toHexString(dtboOffset))
        }
        ret.append(" --pagesize ")
        ret.append(Integer.toString(pageSize))
        ret.append(" --cmdline ")
        ret.append("\"" + cmdline + "\"")
        if (!osVersion.isNullOrBlank()) {
            ret.append(" --os_version ")
            ret.append(osVersion)
        }
        if (!osPatchLevel.isNullOrBlank()) {
            ret.append(" --os_patch_level ")
            ret.append(osPatchLevel)
        }
        ret.append(" --tags_offset ")
        ret.append("0x" + java.lang.Long.toHexString(tagsOffset))
        if (id) {
            ret.append(" --id ")
        }
        ret.append(" --output ")
        ret.append(output + ".google")

        return ret.toString()
    }

    fun toCommandLine(): CommandLine {
        val ret = CommandLine(mkbootimg)
        ret.addArgument(" --header_version ")
        ret.addArgument(headerVersion.toString())
        ret.addArgument(" --base ")
        ret.addArgument("0x" + java.lang.Long.toHexString(base))
        ret.addArgument(" --kernel ")
        ret.addArgument(kernel)
        ret.addArgument(" --kernel_offset ")
        ret.addArgument("0x" + java.lang.Long.toHexString(kernelOffset))
        ramdisk?.let {
            ret.addArgument(" --ramdisk ")
            ret.addArgument(it)
        }
        ret.addArgument(" --ramdisk_offset ")
        ret.addArgument("0x" + java.lang.Long.toHexString(ramdiskOffset))
        second?.let {
            ret.addArgument(" --second ")
            ret.addArgument(it)
        }
        ret.addArgument(" --second_offset ")
        ret.addArgument("0x" + java.lang.Long.toHexString(secondOffset))
        if (!board.isBlank()) {
            ret.addArgument(" --board ")
            ret.addArgument(board)
        }
        if (headerVersion > 0) {
            dtbo?.let { dtbo ->
                ret.addArgument(" --recovery_dtbo ")
                ret.addArgument(dtbo)
            }
            ret.addArgument(" --recovery_dtbo_offset ")
            ret.addArgument("0x" + java.lang.Long.toHexString(dtboOffset))
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
        if (id) {
            ret.addArgument(" --id ")
        }
        ret.addArgument(" --output ")
        ret.addArgument(output + ".google")

        return ret
    }
}
