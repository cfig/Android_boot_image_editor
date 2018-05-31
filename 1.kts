import java.io.*
import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.apache.commons.exec.PumpStreamHandler
import com.fasterxml.jackson.databind.ObjectMapper

fun adbCmd(cmd: String): String {
    val outputStream = ByteArrayOutputStream()
    val exec = DefaultExecutor()
    exec.streamHandler = PumpStreamHandler(outputStream)
    val cmdline = "adb shell $cmd"
    //println(cmdline)
    exec.execute(CommandLine.parse(cmdline))
    //println(outputStream)
    return outputStream.toString().trim()
}

val cpufreqDir = "/sys/devices/system/cpu/cpufreq/policy0"
val interactGov = "/sys/devices/system/cpu/cpufreq/interactive"

val scaling_governor = adbCmd("cat $cpufreqDir/scaling_governor")
val avail_governer = adbCmd("cat $cpufreqDir/scaling_available_governors")
val avail_freq = adbCmd("cat $cpufreqDir/scaling_available_frequencies")
println("Available governers: " + avail_governer)
println("Available frequency: " + avail_freq)

val scaleMax = adbCmd("cat $cpufreqDir/scaling_max_freq")
val scaleMin = adbCmd("cat $cpufreqDir/scaling_min_freq")
println("scaling_X_freq: [$scaleMin, $scaleMax]")
println("Current governer: $scaling_governor")

fun getInteractValue(k: String): String {
    return adbCmd("cat $interactGov/$k")
}
fun getInteractInt(k: String): Int {
    return Integer.decode(adbCmd("cat $interactGov/$k"))
}

data class Boost(
    var boost: Int,
    var boostpulse_duration_ms: Int)
val boostInfo = Boost(getInteractInt("boost"), getInteractInt("boostpulse_duration") / 1000)

data class HiSpeed(
    var load: Int,
    var above_delay_Ms: Int,
    var freq_GHz: Double)
val hiSpeedInfo = HiSpeed(
    getInteractInt("go_hispeed_load"),
    getInteractInt("above_hispeed_delay") / 1000,
    getInteractInt("hispeed_freq") / 1000000.0)

data class InteractiveGov(
    var target_loads: Int,
    var boost: Boost,
    var hiSpeed: HiSpeed,
    var minSampleTimeMs: Int,
    var timerRateMs: Int,
    var timerSlackMs: Int,
    var io_is_busy: Int)

val info = InteractiveGov(
    getInteractInt("target_loads"), 
    boostInfo,
    hiSpeedInfo,
    getInteractInt("min_sample_time") / 1000,
    getInteractInt("timer_rate") / 1000,
    getInteractInt("timer_slack") / 1000,
    getInteractInt("io_is_busy"))

println(ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(info))
