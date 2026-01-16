package cfig.lazybox

import cfig.lazybox.profiler.VideoAnalyzer
import cfig.lazybox.staging.AospCompiledb
import cfig.lazybox.staging.DiffCI
import cfig.lazybox.staging.Perfetto
import cfig.lazybox.staging.RepoWorker
import cfig.lazybox.sysinfo.BootChart
import cfig.lazybox.sysinfo.CpuInfo
import cfig.lazybox.sysinfo.Pidstat
import cfig.lazybox.sysinfo.SysInfo
import cfig.lazybox.sysinfo.ThermalInfo
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.system.exitProcess

class App

fun main(args: Array<String>) {
    val log = LoggerFactory.getLogger(App::class.java)
    if (args.isEmpty()) {
        println("Usage: args: (Array<String>) ...")
        println("   or: function [arguments]...")
        println("\nCurrently defined functions:")
        println("\tcpuinfo gki sysinfo sysstat pidstat bootchart thermal_info compiledb analyze batch_analyze stat apex")
        println("\nCommand Usage:")
        println("bootchart: generate Android bootchart")
        println("gki      : interactive GKI JSON downloader/parser, or process GKI modules from <dir>")
        println("pidstat  : given a pid, profile its CPU usage")
        println("tracecmd : analyze trace-cmd report")
        println("cpuinfo  : get cpu info from /sys/devices/system/cpu/")
        println("sysinfo  : get overall system info from Android")
        println("thermal_info : get thermal info from /sys/class/thermal/")
        println("apex     : find APEX files and mounts on Android device")
        println("analyze [log|video] <log_dir> : analyze device boot performance from video recording and/or logs")
        println("                                default to analyze both video and logs")
        println("batch_analyze <dir> : analyze every immediate subdirectory under <dir>")
        println("stat outlier <dir> : generate boot_time_stability_report.md for all runs under <dir>")
        println("stat compare <a_dir> <b_dir> [--a-name NAME] [--b-name NAME] : compare boot times between A and B")
        println("\nIncubating usage:")
        println("compiledb     : generate compilation database for AOSP")
        println("dmainfo       : parse /d/dma_buf/bufinfo")
        println("diffci        : find changelist files from CI server based on date and time ranges")
        println("repo_lfs      : pull LFS files from Git repositories managed by 'repo'")
        println("repo_unshallow: unshallow Git repositories managed by 'repo'")
        println("perfetto      : generate a Perfetto configuration file")
        println("ina           : probe INA power sensor data")
        exitProcess(0)
    }
    if (args[0] == "cpuinfo") {
        val ret = CpuInfo.construct()
        File("cpuinfo.json").writeText(
            ObjectMapper()
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(ret)
        )
        log.info("cpuinfo.json is ready")
    }
    if (args[0] == "gki") {
        Gki.run(args.drop(1).toTypedArray())
    }
    if (args[0] == "sysinfo") {
        SysInfo().run()
    }
    if (args[0] == "thermal_info") {
        ThermalInfo().run()
    }
    if (args[0] == "sysstat") {
        println("adb shell /data/vendor/sadc -F -L -S ALL 2 20 /data/vendor")
    }
    if (args[0] == "pidstat") {
        Pidstat.run()
    }
    if (args[0] == "bootchart") {
        BootChart.run()
    }
    if (args[0] == "tracecmd") {
        if (args.size == 2) {
            val traceCmdReport = args[1]
            if (File(traceCmdReport).exists()) {
                TraceCmdParser().mainFunction(traceCmdReport)
            } else {
                log.error("File not found: ${args[1]}")
            }
        } else {
            log.error("Usage: tracecmd <report_file>")
        }
    }
    if (args[0] == "split") {
        if (args.size != 3) {
            log.error("Usage: split <workdir> <part_name>")
        }
        CompileCommand().run(args[1], args[2])
    }
    if (args[0] == "rel") {
        ImageRelease.run()
    }
    if (args[0] == "x") {
        AMS.computeRankAndBucket(AMS.getProcRank(), AMS.getStandbyBucket2())
    }
    if (args[0] == "mount") {
        MountAnalyzer().run()
    }
    if (args[0] == "booting") {
        //BootingParser.run()
        BootingParser.run2()
    }
    if (args[0] == "dmainfo") {
        if (args.size != 2) {
            log.error("Usage: dmainfo <dmainfo_file>")
            return
        }
        val dmainfoFile = args[1]
        if (File(dmainfoFile).exists()) {
            val dmaInfoParser = DmaInfoParser()
            val dmaInfo = dmaInfoParser.parse(args.drop(1).toTypedArray())
        } else {
            log.error("File not found: $dmainfoFile")
        }
    }
    if (args[0] == "diffci") {
        DiffCI().run(args.drop(1).toTypedArray())
    }
    if (args[0] == "repo_lfs") {
        RepoWorker().lfsPullRepo(args.drop(1).toTypedArray())
    }
    if (args[0] == "repo_unshallow") {
        RepoWorker().unshallowRepo(args.drop(1).toTypedArray())
    }
    if (args[0] == "perfetto") {
        Perfetto().run(args.drop(1).toTypedArray())
    }
    if (args[0] == "compiledb") {
        AospCompiledb().run()
    }
    if (args[0] == "ina") {
        InaSensor().run()
    }
    if (args[0] == "analyze") {
        val subCommand = args.getOrNull(1)
        if (subCommand == "log") {
            // analyze log <log_dir>
            VideoAnalyzer().analyzeLog(args.drop(2).toTypedArray())
        } else if (subCommand == "video") {
            // analyze video <log_dir>
            VideoAnalyzer().analyzeVideo(args.drop(2).toTypedArray())
        } else if (subCommand == "merge") {
            VideoAnalyzer().mergeReports(args.drop(2).toTypedArray())
        } else {
            // analyze <log_dir>
            val analyzerArgs = args.drop(1).toTypedArray()
            VideoAnalyzer().analyzeVideo(analyzerArgs)
            VideoAnalyzer().analyzeLog(analyzerArgs)
            VideoAnalyzer().mergeReports(analyzerArgs)
        }
    }
    if (args[0] == "batch_analyze") {
        if (args.size != 2) {
            log.error("Usage: batch_analyze <parent_dir>")
            return
        }
        val parentDir = File(args[1])
        if (!parentDir.exists() || !parentDir.isDirectory) {
            log.error("Directory not found: ${parentDir.absolutePath}")
            return
        }

        val children = parentDir.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name } ?: emptyList()
        if (children.isEmpty()) {
            log.warn("No subdirectories found under: ${parentDir.absolutePath}")
            return
        }

        for (child in children) {
            log.info("batch_analyze: ${child.absolutePath}")
            val analyzerArgs = arrayOf(child.absolutePath)
            val analyzer = VideoAnalyzer()
            val videoFile = File(child, "video.mp4")
            val csvFile = File(child, "video.csv")
            if (videoFile.exists() && csvFile.exists()) {
                analyzer.analyzeVideo(analyzerArgs)
            } else {
                log.warn("Skip video analyze (video.mp4/video.csv missing): ${child.absolutePath}")
            }
            analyzer.analyzeLog(analyzerArgs)
            analyzer.mergeReports(analyzerArgs)
        }
    }
    if (args[0] == "stat") {
        val subCommand = args.getOrNull(1)
        if (subCommand == "outlier") {
            BootTimeStability.run(args.drop(2).toTypedArray())
        } else if (subCommand == "compare") {
            BootTimeABCompare.run(args.drop(2).toTypedArray())
        } else {
            log.error("Usage: stat outlier <dir> | stat compare <a_dir> <b_dir>")
        }
    }
    if (args[0] == "apex") {
        Apex().run(args.drop(1).toTypedArray())
    }
}
