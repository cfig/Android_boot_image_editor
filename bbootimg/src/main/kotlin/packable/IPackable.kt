package cfig.packable

import cfig.Avb
import cfig.helper.Helper
import cfig.helper.Helper.Companion.check_call
import cfig.helper.Helper.Companion.check_output
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

@OptIn(ExperimentalUnsignedTypes::class)
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

    fun pull(fileName: String = "dtbo.img", deviceName: String = "dtbo") {
        "adb root".check_call()
        val abUpdateProp = "adb shell getprop ro.build.ab_update".check_output()
        log.info("ro.build.ab_update=$abUpdateProp")
        val slotSuffix = if (abUpdateProp == "true") {
            "adb shell getprop ro.boot.slot_suffix".check_output()
        } else {
            ""
        }
        log.info("slot suffix = $slotSuffix")
        "adb shell dd if=/dev/block/by-name/$deviceName$slotSuffix of=/cache/file.to.pull".check_call()
        "adb pull /cache/file.to.pull $fileName".check_call()
        "adb shell rm /cache/file.to.pull".check_call()
    }

    // invoked solely by reflection
    fun `@verify`(fileName: String) {
        val ai = Avb().parseVbMeta(fileName, true)
        Avb().verify(ai, fileName)
    }

    fun cleanUp() {
        val workDir = Helper.prop("workDir")
        if (File(workDir).exists()) File(workDir).deleteRecursively()
        File(workDir).mkdirs()
    }

    companion object {
        val log: Logger = LoggerFactory.getLogger(IPackable::class.java)
    }
}
