package cfig.lazybox.sysinfo

import cfig.helper.Helper
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText

class ThermalInfo {
    private val log = LoggerFactory.getLogger(ThermalInfo::class.java)

    fun makeTar(tarFile: String, srcDir: String) {
        val pyScript =
            """
import os, sys, subprocess, gzip, logging, shutil, tarfile, os.path
def makeTar(output_filename, source_dir):
   with tarfile.open(output_filename, "w:xz") as tar:
       tar.add(source_dir, arcname=os.path.basename(source_dir))
makeTar("%s", "%s")
""".trim()
        val tmp = Files.createTempFile(Paths.get("."), "xx.", ".yy")
        tmp.writeText(String.format(pyScript, tarFile, srcDir))
        ("python " + tmp.fileName).check_call()
        tmp.deleteIfExists()
    }

    fun run() {
        "adb wait-for-device".check_call()
        "adb root".check_call()
        val prefix = "thermal_info"
        File(prefix).let {
            if (it.exists()) {
                log.info("purging directory $prefix/ ...")
                it.deleteRecursively()
            }
        }
        File(prefix).mkdir()

        val thermalZonesOutput = Helper.powerRun("adb shell ls /sys/class/thermal", null)
        val thermalZones = String(thermalZonesOutput.get(0)).split("\\s+".toRegex()).filter { it.startsWith("thermal_zone") }

        if (thermalZones.isEmpty()) {
            log.warn("No thermal zones found.")
        } else {
            thermalZones.forEach { zone ->
                log.info("pulling info from $zone")
                val zoneDir = File("$prefix/$zone")
                zoneDir.mkdir()
                FileOutputStream("${zoneDir.path}/type").use {
                    SysInfo.runAndWrite("adb shell cat /sys/class/thermal/$zone/type", it, false)
                }
                FileOutputStream("${zoneDir.path}/temp").use {
                    SysInfo.runAndWrite("adb shell cat /sys/class/thermal/$zone/temp", it, false)
                }
            }
        }

        makeTar("$prefix.tar.xz", prefix)
        File(prefix).deleteRecursively()
        log.info("$prefix.tar.xz is ready")
    }

    private fun String.check_call() {
        val ret = Helper.powerRun(this, null)
        val ret2 = String(ret.get(0)).trim()
        if (ret2.isNotEmpty()) {
            log.info(ret2)
        }
    }
}
