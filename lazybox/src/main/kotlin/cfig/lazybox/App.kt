package cfig.lazybox

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
}
