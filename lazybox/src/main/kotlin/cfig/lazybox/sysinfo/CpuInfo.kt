package cfig.lazybox.sysinfo

import cfig.helper.Helper
import org.apache.commons.exec.ExecuteException
import org.slf4j.LoggerFactory

class CpuInfo(
    val cpuinfo: RawCores,
    val policies: List<CpuFreqPolicy>,
    val onDemandGovernor: OndemandGovernor? = null,
    val conservativeGovernor: ConservativeGovernor? = null,
    val schedutilGovernor: SchedutilGovernor? = null,
) {
    companion object {
        fun construct(): CpuInfo {
            val rawCores = RawCores.construct()
            rawCores.toCores()
            val policies = CpuFreqPolicy.construct()
            val onDemand = if (policies.any { it.scaling_governor == "ondemand" }) {
                OndemandGovernor.construct()
            } else {
                null
            }
            val conservative = if (policies.any { it.scaling_governor == "conservative" }) {
                try {
                    ConservativeGovernor.construct()
                } catch (_: ExecuteException) {
                    ConservativeGovernor.construct("/sys/devices/system/cpu/cpu0/cpufreq/conservative")
                }
            } else {
                null
            }
            val sched = if (policies.any { it.scaling_governor == "schedutil" }) {
                SchedutilGovernor.construct()
            } else {
                null
            }
            return CpuInfo(rawCores, policies, onDemand, conservative, sched)
        }

        val getAdbCmdResult: (String, Boolean) -> String? =  { cmd, check ->
            Helper.powerRun2("adb shell $cmd", null).let {
                if (it[0] as Boolean) {
                    String(it[1] as ByteArray).trim()
                } else {
                    if (check) {
                        log.warn(String(it[1] as ByteArray))
                        throw RuntimeException(String(it[2] as ByteArray))
                    } else {
                        log.warn(String(it[1] as ByteArray))
                        log.warn(String(it[2] as ByteArray))
                    }
                    null
                }
            }
        }

        private val log = LoggerFactory.getLogger(CpuInfo::class.java)
    }

    data class SchedutilGovernor(
        var rate_limit_us: Long = 0,
    ) {
        companion object {
            fun construct(): SchedutilGovernor {
                val prefix = "/sys/devices/system/cpu/cpufreq/schedutil"
                return SchedutilGovernor(Helper.adbCmd("cat $prefix/rate_limit_us").toLong())
            }
        }
    }

    data class ConservativeGovernor(
        var down_threshold: Long = 0,
        var up_threshold: Long = 0,
        var ignore_nice_load: Long = 0,
        var sampling_rate: Long = 0,
        var sampling_down_factor: Long = 0,
        var freq_step: Long = 0,
    ) {
        companion object {
            fun construct(prefix: String = "/sys/devices/system/cpu/cpufreq/conservative"): ConservativeGovernor {
                return ConservativeGovernor(
                    Helper.adbCmd("cat $prefix/down_threshold").toLong(),
                    Helper.adbCmd("cat $prefix/up_threshold").toLong(),
                    Helper.adbCmd("cat $prefix/ignore_nice_load").toLong(),
                    Helper.adbCmd("cat $prefix/sampling_rate").toLong(),
                    Helper.adbCmd("cat $prefix/sampling_down_factor").toLong(),
                    Helper.adbCmd("cat $prefix/freq_step").toLong(),
                )
            }
        }
    }

    data class OndemandGovernor(
        var ignore_nice_load: Long? = null,
        var io_is_busy: Long? = null,
        var min_sampling_rate: Long? = null,
        var powersave_bias: Long? = null,
        var sampling_down_factor: Long? = null,
        var sampling_rate: Long? = null,
        var up_threshold: Long? = null,
    ) {
        companion object {
            fun construct(): OndemandGovernor {
                val prefix = "/sys/devices/system/cpu/cpufreq/ondemand"
                return OndemandGovernor(
                    Helper.adbCmd("cat $prefix/ignore_nice_load").toLong(),
                    Helper.adbCmd("cat $prefix/io_is_busy").toLong(),
                    Helper.adbCmd("cat $prefix/min_sampling_rate").toLong(),
                    Helper.adbCmd("cat $prefix/powersave_bias").toLong(),
                    Helper.adbCmd("cat $prefix/sampling_down_factor").toLong(),
                    Helper.adbCmd("cat $prefix/sampling_rate").toLong(),
                    Helper.adbCmd("cat $prefix/up_threshold").toLong(),
                )
            }
        }
    }

    data class RawCores(
        var possible: String,
        var present: String,
        var online: String,
        var offline: String
    ) {
        companion object {
            fun construct(): RawCores {
                return RawCores(
                    Helper.adbCmd("cat /sys/devices/system/cpu/possible"),
                    Helper.adbCmd("cat /sys/devices/system/cpu/present"),
                    Helper.adbCmd("cat /sys/devices/system/cpu/online"),
                    Helper.adbCmd("cat /sys/devices/system/cpu/offline"),
                )
            }
        }

        fun toCores(): Cores {
            return Cores(
                Helper.str2range(possible).get(0),
                Helper.str2range(present).get(0),
                Helper.str2range(online),
                Helper.str2range(offline),
            )
        }
    }

    data class Cores(
        var possible: IntRange = 0..0,
        var present: IntRange = 0..0,
        var online: List<IntRange> = listOf(),
        var offline: List<IntRange> = listOf(),
    )

    class CpuFreqPolicy(
        var name: String = "policyX",
        var affected_cpus: String = "",
        var related_cpus: String = "",

        //cpuinfo
        var cpuinfo_max_freq: String = "",
        var cpuinfo_min_freq: String = "",
        var cpuinfo_cur_freq: String? = null,
        var cpuinfo_transition_latency: String = "",

        //freq
        var scaling_available_frequencies: String = "",
        var scaling_max_freq: String? = null,
        var scaling_min_freq: String = "",
        var scaling_cur_freq: String = "",

        //governor
        var scaling_governor: String? = null,
        var scaling_driver: String = "",
        var scaling_available_governors: String = "",
        var scaling_setspeed: String? = null,
    ) {
        companion object {
            fun construct(): List<CpuFreqPolicy> {
                val ret = mutableListOf<CpuFreqPolicy>()
                val policies = Helper.adbCmd("ls /sys/devices/system/cpu/cpufreq")
                policies.split("\n").forEach {
                    if (it.matches("^policy\\d$".toRegex())) {
                        log.info("Found: $it")
                        ret.add(construct(it.trim()))
                    }
                }
                return ret
            }

            private fun construct(inName: String): CpuFreqPolicy {
                val prefix = "/sys/devices/system/cpu/cpufreq"
                return CpuFreqPolicy(
                    name = inName,
                    affected_cpus = Helper.adbCmd("cat $prefix/$inName/affected_cpus"),
                    related_cpus = Helper.adbCmd("cat $prefix/$inName/related_cpus"),
                    scaling_governor = getAdbCmdResult("cat $prefix/$inName/scaling_governor", false), //HMOS
                    cpuinfo_cur_freq = getAdbCmdResult("cat $prefix/$inName/cpuinfo_cur_freq", false),
                    scaling_available_frequencies = Helper.adbCmd("cat $prefix/$inName/scaling_available_frequencies"),
                    scaling_max_freq = getAdbCmdResult("cat $prefix/$inName/scaling_max_freq", false),
                    cpuinfo_max_freq = Helper.adbCmd("cat $prefix/$inName/cpuinfo_max_freq"),
                    scaling_available_governors = Helper.adbCmd("cat $prefix/$inName/scaling_available_governors"),
                    scaling_min_freq = Helper.adbCmd("cat $prefix/$inName/scaling_min_freq"),
                    cpuinfo_min_freq = Helper.adbCmd("cat $prefix/$inName/cpuinfo_min_freq"),
                    scaling_cur_freq = Helper.adbCmd("cat $prefix/$inName/scaling_cur_freq"),
                    scaling_setspeed = getAdbCmdResult("cat $prefix/$inName/scaling_setspeed", false),
                    cpuinfo_transition_latency = Helper.adbCmd("cat $prefix/$inName/cpuinfo_transition_latency"),
                    scaling_driver = Helper.adbCmd("cat $prefix/$inName/scaling_driver")
                )
            }
        }
    }
}
