// Copyright 2023-2025 yuyezhong@gmail.com
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

package packable

import cfig.bootimg.Common
import cfig.bootimg.v3.VendorBoot
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
        unpackInternal(fileName, Helper.prop("workDir")!!)
    }

    fun unpackInternal(fileName: String, unpackDir: String) {
        //set workdir
        log.info("unpackInternal(fileName: $fileName, unpackDir: $unpackDir)")
        Helper.setProp("workDir", unpackDir)
        clear()
        //create workspace file
        Common.createWorkspaceIni(fileName)
        //create workspace file done

        val outFile = File(workDir, File(fileName).nameWithoutExtension + "." + Helper.prop("config.dts_suffix")).path
        DTC().decompile(fileName, outFile)

        //print summary
        val prints: MutableList<Pair<String, String>> = mutableListOf()
        prints.add(Pair("DTB", fileName))
        prints.add(Pair("DTS", outFile))
        log.info("\n\t\t\tUnpack Summary of {}\n{}\n", fileName, Common.table2String(prints))
    }

    override fun pack(fileName: String) {
        packInternal(Helper.prop("workDir")!!, fileName)
    }

    fun packInternal(workspace: String, outFileName: String) {
        log.info("packInternal($workspace, $outFileName)")
        Helper.setProp("workDir", workspace)
        //workspace+cfg
        val iniRole = Common.loadProperties(File(workspace, "workspace.ini").canonicalPath).getProperty("role")
        val dtsSrc = File(workDir, File(iniRole).nameWithoutExtension + "." + Helper.prop("config.dts_suffix")).path

        val origFile = File(workDir, File(outFileName).name + ".orig").path
        log.info("COPY $outFileName -> $origFile")
        File(outFileName).copyTo(File(origFile), overwrite = true)
        check(DTC().compile(dtsSrc, outFileName)) { "fail to compile dts" }

        //print summary
        val prints: MutableList<Pair<String, String>> = mutableListOf()
        prints.add(Pair("DTS", dtsSrc))
        prints.add(Pair("updated DTB", outFileName))
        log.info("\n\t\t\tPack Summary of {}\n{}\n", outFileName, Common.table2String(prints))
    }

    fun pull(fileName: String) {
        //prepare
        super.clear()
        File(workDir).mkdir()

        //pull
        "adb root".check_call()
        var hw = "linux"
        if ("adb shell which getprop".check_output().isBlank()) { //linux
            "adb push tools/bin/dtc-linux /data/vendor/dtc".check_call()
        } else { //android
            "adb push tools/bin/dtc-android /data/vendor/dtc".check_call()
            hw = "adb shell getprop ro.hardware".check_output()
            log.info("ro.hardware=$hw")
        }
        "adb shell /data/vendor/dtc -I fs /proc/device-tree -o /data/vendor/file.to.pull".check_call()
        "adb pull /data/vendor/file.to.pull $workDir$hw.dts".check_call()
        "adb shell /data/vendor/dtc -I fs -O dtb /proc/device-tree -o /data/vendor/file.to.pull".check_call()
        "adb pull /data/vendor/file.to.pull $hw.dtb".check_call()
        "adb shell rm /data/vendor/file.to.pull".check_call()
        "adb shell rm /data/vendor/dtc".check_call()
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