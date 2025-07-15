package cfig.lazybox.staging

import java.io.File
import org.slf4j.LoggerFactory

class Perfetto {
    private val log = LoggerFactory.getLogger(Perfetto::class.java.simpleName)

    /**
     * Main entry method to generate the configuration file.
     */
    fun run(args: Array<String>) {
        val options = args.toSet()

        // Check for help flags
        if ("-h" in options || "--help" in options) {
            printUsage()
            return
        }

        log.info("Running Perfetto configuration generation with arguments: ${args.joinToString(", ")}")
        val outputFileName = "pftrace.cfg"
        val finalConfig = generatePerfettoConfig(options)

        try {
            File(outputFileName).writeText(finalConfig)
            log.info("Successfully wrote Perfetto configuration to '$outputFileName'.")
            log.info("Options used: ${options.ifEmpty { setOf("default") }}")

            if ("+boot" in options) {
                val adbCommands = """

                    --------------------------------------------------------------------
                    Boot trace config enabled. To capture a boot trace, run these commands:

                    [enable tracing]
                    adb root
                    adb push pftrace.cfg /data/misc/perfetto-configs/boottrace.pbtxt
                    adb shell setprop persist.debug.perfetto.boottrace 1

                    [pull trace result after reboot]
                    adb pull /data/misc/perfetto-traces/boottrace.perfetto-trace
                    --------------------------------------------------------------------
                """.trimIndent()
                println(adbCommands)
            }
        } catch (e: Exception) {
            log.error("Failed to write to file '$outputFileName'. Reason: ${e.message}")
        }
    }

    /**
     * Prints the help/usage message for the script.
     */
    private fun printUsage() {
        val usage = """
            Usage: kotlin Perfetto.main.kts [options...]

            Generates a pftrace.cfg file for Perfetto tracing based on specified options.
            The output file is named 'pftrace.cfg'.

            Options:
              -h, --help        Show this help message and exit.

              +power            Include ftrace power events and Android power rail data sources.
              +atrace           Include a wide range of atrace categories for detailed app tracing.
              +lmk              Include Low Memory Killer (LMK) and OOM score ftrace events.
              +cpu_usage        Include detailed CPU time and process fork count statistics.
              +logcat           Include all Android logcat buffers in the trace.
              +sf               Include SurfaceFlinger frame timeline data.
              +boot             Enable boot tracing. After generating the config, this prints the
                                necessary adb commands to set up the trace for the next boot.
        """.trimIndent()
        println(usage)
    }

    /**
     * Builds the Perfetto configuration string based on the provided options.
     */
    fun generatePerfettoConfig(options: Set<String> = emptySet()): String {
        log.info("Generating Perfetto configuration with options: $options")
        val configBuilder = StringBuilder()

        // --- 1. Base Config: Buffers ---
        log.info("Adding buffers")
        configBuilder.append(
            """
            # === Buffers ===
            buffers: {
                size_kb: 63488
                fill_policy: DISCARD
            }
            buffers: {
                size_kb: 2048
                fill_policy: DISCARD
            }

            """.trimIndent()
        )

        // --- 2. Base Config: Core Data Sources (excluding sys_stats) ---
        log.info("Adding core data sources")
        configBuilder.append(
            """
            # === Base Data Sources ===
            data_sources: {
                config {
                    name: "linux.process_stats"
                    process_stats_config {
                        scan_all_processes_on_start: true
                        proc_stats_poll_ms: 250
                    }
                }
            }
            data_sources: {
                config {
                    name: "android.packages_list"
                    target_buffer: 1
                }
            }
            """.trimIndent()
        )

        // --- 3. Dynamic and Optional Config ---

        // Dynamically build linux.sys_stats config
        val sysStatsLines = mutableListOf(
            "meminfo_period_ms: 250",
            "vmstat_period_ms: 250",
            "cpufreq_period_ms: 250"
        )
        if ("+cpu_usage" in options) {
            log.info("Adding data sources for: +cpu_usage")
            sysStatsLines.add("stat_period_ms: 250")
            sysStatsLines.add("stat_counters: STAT_CPU_TIMES")
            sysStatsLines.add("stat_counters: STAT_FORK_COUNT")
        }

        log.info("Appending sys_stats data source to config")
        configBuilder.append("\n\n# === System-wide Stats ===")
        configBuilder.append(
            """
            data_sources: {
                config {
                    name: "linux.sys_stats"
                    sys_stats_config {
            """.trimIndent()
        )
        sysStatsLines.forEach { configBuilder.append("\n                        $it") }
        configBuilder.append(
            """
                    }
                }
            }
            """.trimIndent()
        )

        // Dynamically build ftrace config
        val ftraceEvents = mutableListOf<String>()
        val atraceCategories = mutableListOf<String>()
        val atraceApps = mutableListOf<String>()

        if ("+power" in options) {
            ftraceEvents.add("power/*")
        }
        if ("+atrace" in options) {
            ftraceEvents.add("ftrace/print")
            atraceCategories.addAll(
                listOf(
                    "adb", "aidl", "am", "audio", "binder_driver", "binder_lock",
                    "bionic", "camera", "dalvik", "database", "gfx", "hal",
                    "input", "network", "nnapi", "pm", "power", "res", "rro",
                    "rs", "sm", "ss", "vibrator", "video", "view", "webview", "wm"
                )
            )
        }
        if ("+lmk" in options) {
            log.info("Adding data sources for: +lmk")
            ftraceEvents.add("lowmemorykiller/lowmemory_kill")
            ftraceEvents.add("oom/oom_score_adj_update")
            atraceApps.add("lmkd")
        }

        if (ftraceEvents.isNotEmpty() || atraceCategories.isNotEmpty() || atraceApps.isNotEmpty()) {
            log.info("Appending ftrace data source to config")
            configBuilder.append("\n\n# === Optional Ftrace/Atrace Tracing ===")
            configBuilder.append(
                """
                data_sources: {
                    config {
                        name: "linux.ftrace"
                        ftrace_config {
                """.trimIndent()
            )
            ftraceEvents.forEach { configBuilder.append("\n                            ftrace_events: \"$it\"") }
            atraceCategories.forEach { configBuilder.append("\n                            atrace_categories: \"$it\"") }
            atraceApps.forEach { configBuilder.append("\n                            atrace_apps: \"$it\"") }
            configBuilder.append(
                """
                        }
                    }
                }
                """.trimIndent()
            )
        }

        if ("+power" in options) {
            log.info("Adding data sources for: android.power")
            configBuilder.append(
                """

                # === Optional Android Power Tracing ===
                data_sources: {
                    config {
                        name: "android.power"
                        android_power_config {
                            battery_poll_ms: 250
                            battery_counters: BATTERY_COUNTER_CAPACITY_PERCENT
                            battery_counters: BATTERY_COUNTER_CHARGE
                            battery_counters: BATTERY_COUNTER_CURRENT
                            collect_power_rails: true
                        }
                    }
                }
                """.trimIndent()
            )
        }

        if ("+logcat" in options) {
            log.info("Adding data sources for: +logcat")
            configBuilder.append(
                """
                # === Optional Logcat Tracing ===
                data_sources {
                  config {
                    name: "android.log"
                    android_log_config {
                      log_ids: LID_CRASH
                      log_ids: LID_DEFAULT
                      log_ids: LID_EVENTS
                      log_ids: LID_KERNEL
                      log_ids: LID_RADIO
                      log_ids: LID_SECURITY
                      log_ids: LID_STATS
                      log_ids: LID_SYSTEM
                    }
                  }
                }
                """.trimIndent()
            )
        }

        // (REVISED) Add SurfaceFlinger data source if option is present
        if ("+sf" in options) {
            log.info("Adding data sources for: +sf")
            configBuilder.append(
                """
                # === Optional SurfaceFlinger Tracing ===
                data_sources {
                  config {
                    name: "android.surfaceflinger.frametimeline"
                  }
                }
                """.trimIndent()
            )
        }

        // --- 4. Trace Duration ---
        log.info("Adding trace duration")
        configBuilder.append(
            """
            # === Trace Duration ===
            # The total duration of the trace in milliseconds.
            duration_ms: 15000
            """.trimIndent()
        )

        return configBuilder.toString()
    }
}
