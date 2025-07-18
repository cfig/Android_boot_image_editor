package cfig.lazybox

import cfig.lazybox.staging.DiffCI
import cfig.lazybox.staging.Perfetto
import cfig.lazybox.staging.RepoWorker
import cfig.lazybox.sysinfo.BootChart
import cfig.lazybox.sysinfo.CpuInfo
import cfig.lazybox.sysinfo.Pidstat
import cfig.lazybox.sysinfo.SysInfo
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
        println("\tcpuinfo sysinfo sysstat pidstat bootchart")
        println("\nCommand Usage:")
        println("bootchart: generate Android bootchart")
        println("pidstat  : given a pid, profile its CPU usage")
        println("tracecmd : analyze trace-cmd report")
        println("cpuinfo  : get cpu info from /sys/devices/system/cpu/")
        println("sysinfo  : get overall system info from Android")
        println("\nIncubating usage:")
        println("apps          : get apk file list from Android")
        println("dmainfo       : parse /d/dma_buf/bufinfo")
        println("diffci        : find changelist files from CI server based on date and time ranges")
        println("repo_lfs      : pull LFS files from Git repositories managed by 'repo'")
        println("repo_unshallow: unshallow Git repositories managed by 'repo'")
        println("perfetto      : generate a Perfetto configuration file")
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
    if (args[0] == "sysinfo") {
        SysInfo().run()
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
    if (args[0] == "apps") {
        //AppList.retrieveList()
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
}
