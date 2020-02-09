package cfig.packable

import cfig.Helper.Companion.check_call
import cfig.Helper.Companion.check_output
import cfig.UnifiedConfig
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

@ExperimentalUnsignedTypes
interface IPackable {
    val loopNo: Int
    fun capabilities(): List<String> {
        return listOf("^dtbo\\.img$")
    }

    fun unpack(fileName: String = "dtbo.img")
    fun pack(fileName: String = "dtbo.img")
    fun flash(fileName: String = "dtbo.img", deviceName: String = "dtbo") {
        "adb root".check_call()
        val abUpdateProp = "adb shell getprop ro.build.ab_update".check_output()
        log.info("ro.build.ab_update=$abUpdateProp")
        val slotSuffix = if (abUpdateProp == "true") {
            "adb shell getprop ro.boot.slot_suffix".check_output()
        } else {
            ""
        }
        log.info("slot suffix = $slotSuffix")
        "adb push $fileName /cache/file.to.burn".check_call()
        "adb shell dd if=/cache/file.to.burn of=/dev/block/by-name/$deviceName$slotSuffix".check_call()
        "adb shell rm /cache/file.to.burn".check_call()
    }

    fun cleanUp() {
        if (File(UnifiedConfig.workDir).exists()) File(UnifiedConfig.workDir).deleteRecursively()
        File(UnifiedConfig.workDir).mkdirs()
    }

    companion object {
        val log: Logger = LoggerFactory.getLogger(IPackable::class.java)
    }
}
