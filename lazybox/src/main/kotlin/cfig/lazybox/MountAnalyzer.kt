package cfig.lazybox

import org.slf4j.LoggerFactory
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter

class MountAnalyzer {
    data class MountInfo(
        var dev: String = "",
        var mountPoint: String = "",
        var fsType: String = "",
        var flags: String? = null,
    )

    class MiComparator : Comparator<MountInfo> {
        override fun compare(p1: MountInfo, p2: MountInfo): Int {
            var ret = p1.fsType.compareTo(p2.fsType) * 100
            ret += p1.dev.compareTo(p2.dev) * 10
            ret += p1.mountPoint.compareTo(p2.mountPoint) * 1
            return ret
        }
    }

    fun run() {
        val loopApex = mutableListOf<MountInfo>()
        val dmApex = mutableListOf<MountInfo>()
        val tmpApex = mutableListOf<MountInfo>()
        val bootApex = mutableListOf<MountInfo>()
        val fuseInfo = mutableListOf<MountInfo>()
        val sysInfo = mutableListOf<MountInfo>()
        val androidRo = mutableListOf<MountInfo>()
        val androidRw = mutableListOf<MountInfo>()
        val otherRw = mutableListOf<MountInfo>()
        val unknownMi = mutableListOf<MountInfo>()
        val lines = File("mount.log").readLines()
        lines.forEachIndexed { n, line ->
            val regex = Regex("(\\S+)\\s+on\\s+(\\S+)\\s+type\\s+(\\w+)\\s+\\(([^)]*)\\)") // Capture flags
            val matchResult = regex.find(line)
            if (matchResult != null) {
                val dev = matchResult.groupValues[1]
                val mountPoint = matchResult.groupValues[2]
                val fsType = matchResult.groupValues[3]
                val flags =
                    if (matchResult.groupValues.size > 4) matchResult.groupValues[4] else null // Handle no flags
                val mi = MountInfo(dev, mountPoint, fsType, flags)
                if (mi.mountPoint.startsWith("/apex") || mi.mountPoint.startsWith("/bootstrap-apex")) {
                    if (mi.mountPoint.startsWith("/bootstrap-apex")) {
                        bootApex.add(mi)
                    } else if (mi.dev.startsWith("/dev/block/loop")) {
                        loopApex.add(mi)
                    } else if (mi.dev.startsWith("/dev/block/dm")) {
                        dmApex.add(mi)
                    } else if (mi.dev.startsWith("tmpfs")) {
                        tmpApex.add(mi)
                    } else {
                        log.info("$fsType: $dev -> $mountPoint")
                        throw IllegalStateException("X1")
                    }
                } else if (mi.mountPoint.startsWith("/sys/") || mi.mountPoint == "/sys") {
                    sysInfo.add(mi)
                } else if (mi.fsType == "fuse") {
                    fuseInfo.add(mi)
                } else {
                    log.info("$fsType: $dev -> $mountPoint")
                    if (mi.flags!!.contains("ro,") or mi.flags!!.contains("ro)")) {
                        androidRo.add(mi)
                    } else if (mi.flags!!.contains("rw,") or mi.flags!!.contains("rw)")) {
                        if (mi.dev.startsWith("/dev/")) {
                            androidRw.add(mi)
                        } else {
                            otherRw.add(mi)
                        }
                    } else {
                        throw IllegalStateException("X2")
                    }
                }
            } else { //For lines without flags
                val regexNoFlags = Regex("(\\S+)\\s+on\\s+(\\S+)\\s+type\\s+(\\w+)")
                val matchResultNoFlags = regexNoFlags.find(line)
                if (matchResultNoFlags != null) {
                    val dev = matchResultNoFlags.groupValues[1]
                    val mountPoint = matchResultNoFlags.groupValues[2]
                    val fsType = matchResultNoFlags.groupValues[3]
                    val mi = MountInfo(dev, mountPoint, fsType, null)
                    unknownMi.add(mi)
                } else {
                    throw IllegalStateException("X3")
                }
            }
        } // end-of-lines
        //sanity check, make sure consistent
        check(
            listOf(
                loopApex,
                dmApex,
                tmpApex,
                bootApex,
                fuseInfo,
                sysInfo,
                androidRo,
                androidRw,
                otherRw,
                unknownMi
            ).sumOf { it.size } == lines.size)
        //dump
        val infoNames = listOf(
            "fusefs",
            "sysfs",
            "Android RO",
            "Android RW",
            "other Rw",
            "loop apex",
            "dm apex",
            "tmp apex",
            "boot apex",
            "unknown"
        )
        BufferedWriter(FileWriter(File("sorted_mount.log"))).use { fos ->
            listOf(
                fuseInfo,
                sysInfo,
                androidRo,
                androidRw,
                otherRw,
                loopApex,
                dmApex,
                tmpApex,
                bootApex,
                unknownMi
            ).forEachIndexed { n, mis ->
                mis.sortWith(MiComparator())
                log.info(infoNames.get(n))
                fos.write(infoNames.get(n) + "\n")
                mis.forEachIndexed { index, it ->
                    log.info("[$index] ${it.fsType} : ${it.dev} -> ${it.mountPoint} (${it.flags})")
                    fos.write("#$index | ${it.fsType} | ${it.dev} | ${it.mountPoint} | (${it.flags})\n")
                }
                fos.write("\n")
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(MountAnalyzer::class.java)
    }
}