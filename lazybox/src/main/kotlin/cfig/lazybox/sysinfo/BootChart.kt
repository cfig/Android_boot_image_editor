package cfig.lazybox.sysinfo

import cfig.helper.Helper
import cfig.helper.Helper.Companion.check_call
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

class BootChart {
    companion object {
        private val log = LoggerFactory.getLogger(BootChart::class.java)
        fun run() {
            "adb wait-for-device".check_call()
            "adb root".check_call()
            "adb wait-for-device".check_call()
            "adb shell touch /data/bootchart/enabled".check_call()
            Helper.adbCmd("reboot")
            "adb wait-for-device".check_call()
            "adb root".check_call()
            "adb wait-for-device".check_call()
            Helper.adbCmd("rm -fv /data/bootchart/enabled")
            while (true)  {
                val comp = Helper.adbCmd("getprop sys.boot_completed")
                if (comp == "1") {
                    log.info("boot completed")
                    TimeUnit.SECONDS.sleep(3)
                    break
                } else {
                    log.info("still booting ...")
                    TimeUnit.SECONDS.sleep(1)
                }
            }
            "header proc_stat.log proc_ps.log proc_diskstats.log".split("\\s".toRegex()).forEach {
                val LOGROOT = "/data/bootchart/"
                "adb pull ${LOGROOT}$it".check_call()
            }
            "tar -czf bootchart.tgz header proc_stat.log proc_ps.log proc_diskstats.log".check_call()
            "pybootchartgui bootchart.tgz".check_call()
            "xdg-open bootchart.png".check_call()
        }
    }
}