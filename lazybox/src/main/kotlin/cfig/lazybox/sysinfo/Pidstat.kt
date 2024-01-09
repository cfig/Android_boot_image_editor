package cfig.lazybox.sysinfo

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.Path
import kotlin.io.path.deleteIfExists

class Pidstat {
    data class Cfg(
        var pid: Long? = null,
        var pidof: String? = null,
        var pidstat_cmd_pattern: String? = "pservice",
        var show_thread: Boolean = true,
        var interval: Int = 3,
        var iteration: Int? = null,
        var top_sort_by: Int = 9
    )
    companion object {
        private val log = LoggerFactory.getLogger(Cfg::class.java)
        fun run() {
            val outFile = "run%d.sh"
            File(/* pathname = */ "pid.json").let { pidf ->
                if (!pidf.exists()) {
                    ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(pidf, Pidstat.Cfg())
                    log.info(pidf.name + " is ready")
                } else {
                    val cfg = ObjectMapper().readValue(pidf, Pidstat.Cfg::class.java) as Pidstat.Cfg
                    var run1 = true
                    var run2 = true
                    //pid
                    val cmd_pid: String
                    if (cfg.pid != null) {
                        cmd_pid = "-p ${cfg.pid}"
                        log.info("pid found")
                    } else if (cfg.pidof?.isNotBlank() == true) {
                        cmd_pid = "-p \\$\\(pidof\\ ${cfg.pidof}\\)"
                        log.info("pidof found")
                    } else if (cfg.pidstat_cmd_pattern?.isNotBlank() == true) {
                        cmd_pid = "-G ${cfg.pidstat_cmd_pattern}"
                        run2 = false
                    } else {
                        throw IllegalArgumentException("no pid to stat")
                    }
                    //interval
                    val cmd_interval = cfg.interval.toString()
                    //iteration
                    val cmd_iteration1 = if (cfg.iteration != null) cfg.iteration else ""
                    val cmd_iteration2 = if (cfg.iteration != null) "-n ${cfg.iteration}" else ""
                    //thread
                    val cmd_thread1 = if (cfg.show_thread) "-t" else ""
                    val cmd_thread2 = if (cfg.show_thread) "-H" else ""
                    //sort
                    val cmd_sort2 = "-s ${cfg.top_sort_by}"

                    val cmd1 = "adb shell /data/vendor/pidstat $cmd_pid -u $cmd_thread1 -l $cmd_interval $cmd_iteration1"
                    val cmd2 = "adb shell top $cmd_thread2 $cmd_pid $cmd_iteration2 $cmd_sort2"

                    val ownerWritable = PosixFilePermissions.fromString("rwxr-xr-x")
                    val permissions: FileAttribute<*> = PosixFilePermissions.asFileAttribute(ownerWritable)

                    String.format(outFile, 1).let {
                        Path(it).deleteIfExists()
                        Files.createFile(Path(it), permissions)
                        File(it).writeText("#!/bin/bash\nset -x\n$cmd1\n")
                        log.info("$it is ready")
                    }
                    String.format(outFile, 2).let {
                        Path(it).deleteIfExists()
                        if (run2) {
                            Files.createFile(Path(it), permissions)
                            File(it).writeText("#!/bin/bash\necho $cmd2\n$cmd2\n")
                            log.info("$it is ready")
                        } else {
                            log.warn("$it not feasible")
                        }
                    }
                }
            }
        }
    }
}