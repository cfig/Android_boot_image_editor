package packable

import cfig.bootimg.Common
import cfig.helper.Helper
import cfig.helper.Helper.Companion.check_call
import cfig.helper.Helper.Companion.check_output
import cfig.packable.IPackable
import org.slf4j.LoggerFactory
import rom.fdt.DTC
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.deleteIfExists

class DeviceTreeParser : IPackable {
    override fun capabilities(): List<String> {
        return listOf("^.*\\.dtb$")
    }
    override val loopNo: Int
        get() = 1

    override fun unpack(fileName: String) {
        super.clear()
        log.info("unpacking $fileName")
        val outFile = workDir + fileName.removeSuffix(".dtb") + "." + Helper.prop("config.dts_suffix")
        DTC().decompile(fileName, outFile)

        //print summary
        val prints: MutableList<Pair<String, String>> = mutableListOf()
        prints.add(Pair("DTB", fileName))
        prints.add(Pair("DTS", outFile))
        log.info("\n\t\t\tUnpack Summary of {}\n{}\n", fileName, Common.table2String(prints))
    }

    override fun pack(fileName: String) {
        log.info("packing $fileName")
        val outFile = workDir + fileName.removeSuffix(".dtb") + "." + Helper.prop("config.dts_suffix")
        check(DTC().compile(outFile, "$fileName.new")) { "fail to compile dts" }

        //print summary
        val prints: MutableList<Pair<String, String>> = mutableListOf()
        prints.add(Pair("DTS", outFile))
        prints.add(Pair("updated DTB", "$fileName.new"))
        log.info("\n\t\t\tPack Summary of {}\n{}\n", fileName, Common.table2String(prints))
    }

    override fun pull(fileName: String, deviceName: String) {
        //prepare
        super.clear()
        File(workDir).mkdir()

        //pull
        "adb root".check_call()
        "adb push tools/bin/dtc-android /data/vendor/dtc-android".check_call()
        val hw = "adb shell getprop ro.hardware".check_output()
        log.info("ro.hardware=$hw")
        "adb shell /data/vendor/dtc-android -I fs /proc/device-tree -o /data/vendor/file.to.pull".check_call()
        "adb pull /data/vendor/file.to.pull $workDir$hw.dts".check_call()
        "adb shell /data/vendor/dtc-android -I fs -O dtb /proc/device-tree -o /data/vendor/file.to.pull".check_call()
        "adb pull /data/vendor/file.to.pull $hw.dtb".check_call()
        "adb shell rm /data/vendor/file.to.pull".check_call()
        "adb shell rm /data/vendor/dtc-android".check_call()
        if (fileName != "$hw.dtb") {
            File(fileName).delete()
            log.warn("deleting intermediate dtb file: $fileName")
        }

        //print summary
        val prints: MutableList<Pair<String, String>> = mutableListOf()
        prints.add(Pair("source", "/proc/device-tree"))
        prints.add(Pair("DTB", "$hw.dtb"))
        prints.add(Pair("DTS", "$workDir$hw.dts"))
        log.info("\n\t\t\tPull Summary of {}\n{}\n", "$hw.dtb", Common.table2String(prints))
    }

    fun clear(fileName: String) {
        super.clear()
        listOf("", ".new").forEach {
            Path("$fileName$it").deleteIfExists()
        }
    }

    private val log = LoggerFactory.getLogger(DeviceTreeParser::class.java)
    private val workDir = Helper.prop("workDir")
}