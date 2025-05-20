package cfig.lazybox

import org.slf4j.LoggerFactory
import java.io.File
import java.util.regex.Pattern

class BootingParser {
    companion object {
        private val log = LoggerFactory.getLogger(BootingParser::class.java)
        fun run() {
            val logLines = File("booting.log").readLines()
            val regex = Pattern.compile("""\[([^]]+)] \[\s*([0-9.]+)]\[\s*(T\d+)] init: starting service '([^']+)'.*""")
            for (line in logLines) {
                val matcher = regex.matcher(line)
                if (matcher.find()) {
                    val timestamp = matcher.group(1)
                    val kernelTime = matcher.group(2)
                    val tLevel = matcher.group(3)
                    val serviceName = matcher.group(4)

                    println("Timestamp: $timestamp, Kernel Time: $kernelTime, T-Level: $tLevel, Service Name: $serviceName")
                }
            }
        }

        fun run2() {
            val logLines = File("booting.log2").readLines()


            val actionRegex = Pattern.compile("""\[([^]]+)] \[\s*([0-9.]+)]\[\s*(T\d+)] init: processing action \(([^)]+)\) from \(([^)]+)\).*""")
            val commandRegex = Pattern.compile("""\[([^]]+)] \[\s*([0-9.]+)]\[\s*(T\d+)] init: Command '([^']+)' action=([^\(]+) \(([^)]+)\) took (\d+)ms and (succeeded|failed)(.*)?""")
            val svcExecRegex = Pattern.compile("""\[([^]]+)] \[\s*([0-9.]+)]\[\s*(T\d+)] init: SVC_EXEC service '([^']+)' pid (\d+) \(([^)]+)\) started; waiting\.""")
            val serviceStartRegex = Pattern.compile("""\[([^]]+)] \[\s*([0-9.]+)]\[\s*(T\d+)] init: starting service '([^']+)'.*""")

            for (line in logLines) {
                val actionMatcher = actionRegex.matcher(line)
                if (actionMatcher.find()) {
                    val timestamp = actionMatcher.group(1)
                    val kernelTime = actionMatcher.group(2)
                    val tLevel = actionMatcher.group(3)
                    val actionName = actionMatcher.group(4)
                    val fromComponent = actionMatcher.group(5)

                    println("Timestamp: $timestamp, Kernel Time: $kernelTime, T-Level: $tLevel, Action Name: $actionName, From: $fromComponent")
                }

                val commandMatcher = commandRegex.matcher(line)
                if (commandMatcher.find()) {
                    val timestamp = commandMatcher.group(1)
                    val kernelTime = commandMatcher.group(2)
                    val tLevel = commandMatcher.group(3)
                    val command = commandMatcher.group(4)
                    val action = commandMatcher.group(5).trim()
                    val fromComponent = commandMatcher.group(6)
                    val duration = commandMatcher.group(7)
                    val status = commandMatcher.group(8)
                    val failReason = commandMatcher.group(9)?.trim()

                    println("Timestamp: $timestamp, Kernel Time: $kernelTime, T-Level: $tLevel, Command: $command, Action: $action, From: $fromComponent, Duration: ${duration}ms, Status: $status${if (failReason != null) ", Reason: $failReason" else ""}")
                }

                val svcExecMatcher = svcExecRegex.matcher(line)
                if (svcExecMatcher.find()) {
                    val timestamp = svcExecMatcher.group(1)
                    val kernelTime = svcExecMatcher.group(2)
                    val tLevel = svcExecMatcher.group(3)
                    val serviceName = svcExecMatcher.group(4)
                    val pid = svcExecMatcher.group(5)
                    val context = svcExecMatcher.group(6)

                    println("Timestamp: $timestamp, Kernel Time: $kernelTime, T-Level: $tLevel, Service Name: $serviceName, PID: $pid, Context: $context")
                }

                val serviceStartMatcher = serviceStartRegex.matcher(line)
                if (serviceStartMatcher.find()) {
                    val timestamp = serviceStartMatcher.group(1)
                    val kernelTime = serviceStartMatcher.group(2)
                    val tLevel = serviceStartMatcher.group(3)
                    val serviceName = serviceStartMatcher.group(4)

                    println("Timestamp: $timestamp, Kernel Time: $kernelTime, T-Level: $tLevel, Service Name: $serviceName")
                }
            }




        } // end-of-fun
    } // end-of-companion
}