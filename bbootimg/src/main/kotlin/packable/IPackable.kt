// Copyright 2021 yuyezhong@gmail.com
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package cfig.packable

import avb.AVBInfo
import cfig.Avb
import cfig.helper.Dumpling
import cfig.helper.Helper
import cfig.helper.Helper.Companion.check_call
import cfig.helper.Helper.Companion.check_output
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.deleteIfExists

interface IPackable {
    val loopNo: Int
    val outDir: String
        get() = Helper.prop("workDir")

    fun capabilities(): List<String> {
        return listOf("^dtbo\\.img$")
    }

    fun unpack(fileName: String = "dtbo.img")
    fun pack(fileName: String = "dtbo.img")
    fun flash(fileName: String = "dtbo.img", deviceName: String = "dtbo") {
        "adb root".check_call()
        val abUpdateProp = "adb shell getprop ro.build.ab_update".check_output()
        log.info("ro.build.ab_update=$abUpdateProp")
        val slotSuffix = if (abUpdateProp == "true" && !fileName.startsWith("misc.img")) {
            "adb shell getprop ro.boot.slot_suffix".check_output()
        } else {
            ""
        }
        log.info("slot suffix = $slotSuffix")
        "adb push $fileName /mnt/file.to.burn".check_call()
        "adb shell dd if=/mnt/file.to.burn of=/dev/block/by-name/$deviceName$slotSuffix".check_call()
        "adb shell rm /mnt/file.to.burn".check_call()
    }

    fun pull(fileName: String = "dtbo.img", deviceName: String = "dtbo") {
        "adb root".check_call()
        val abUpdateProp = "adb shell getprop ro.build.ab_update".check_output()
        log.info("ro.build.ab_update=$abUpdateProp")
        val slotSuffix = if (abUpdateProp == "true" && !fileName.startsWith("misc.img")) {
            "adb shell getprop ro.boot.slot_suffix".check_output()
        } else {
            ""
        }
        log.info("slot suffix = $slotSuffix")
        "adb shell dd if=/dev/block/by-name/$deviceName$slotSuffix of=/mnt/file.to.pull".check_call()
        "adb pull /mnt/file.to.pull $fileName".check_call()
        "adb shell rm /mnt/file.to.pull".check_call()
    }

    // invoked solely by reflection
    fun `@verify`(fileName: String) {
        val ai = AVBInfo.parseFrom(Dumpling(fileName)).dumpDefault(fileName)
        Avb.verify(ai, Dumpling(fileName, fileName))
    }

    fun clear() {
        val workDir = Helper.prop("workDir")
        if (File(workDir).exists()) {
            log.info("deleting $workDir ...")
            File(workDir).deleteRecursively()
        }
        File(workDir).mkdirs()
        // java.nio.file.Files.deleteIfExists() will throw exception on Windows platform, so use java.io.File
        // Caused by: java.nio.file.FileSystemException: uiderrors: The process cannot access the file because it is being used by another process
        File("uiderrors").deleteOnExit()
    }

    companion object {
        val log: Logger = LoggerFactory.getLogger(IPackable::class.java)
    }
}
