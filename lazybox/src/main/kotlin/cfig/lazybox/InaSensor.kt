package cfig.lazybox

import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class InaSensor {
    val OUTPUT_FILENAME = "power_report.md"

    data class ChannelData(
        var voltageMv: Long? = null,
        var voltageNode: String? = null, // e.g.: "in1_input"
        var currentMa: Long? = null,
        var currentNode: String? = null,  // e.g.: "curr1_input"
        var powerUw: Long? = null,
        var powerNode: String? = null,    // e.g.: "power1_input"
        var label: String? = null,        // e.g.: "VDD_Main"
        var labelNode: String? = null     // e.g.: "in1_label"
    )

    fun runHostCommand(vararg command: String): String {
        try {
            val process = ProcessBuilder(*command)
                .redirectErrorStream(true) // merge stderr into stdout for easier reading
                .start()

            val output = process.inputStream.bufferedReader().use { it.readText().trim() }

            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroy()
                // This is a fatal error, print and exit
                println("Error: '${command.joinToString(" ")}' command timed out.")
                exitProcess(1)
            }

            val exitCode = process.exitValue()
            if (exitCode != 0) {
                if (output.contains("no devices/emulators found")) {
                    println("Error: '${command.joinToString(" ")}' failed, exit code: $exitCode")
                    println("Command output: $output")
                    println("--> Hint: Please ensure your Android device is connected and authorized via adb.")
                    exitProcess(1)
                }
                // Other errors (e.g., missing 'ls') will be handled by runAdbShell
            }

            return output

        } catch (e: java.io.IOException) {
            println("Error: I/O exception while running command: ${e.message}")
            if ("No such file or directory" in (e.message ?: "") || "Cannot run program \"adb\"" in (e.message ?: "")) {
                println("--> Hint: Is the 'adb' command installed and available in your system PATH?")
            }
            exitProcess(1)
        } catch (e: Exception) {
            println("Error: Unknown exception while running command: ${e.message}")
            exitProcess(1)
        }
    }

    fun runAdbShell(shellCommand: String): String {
        val output = runHostCommand("adb", "shell", shellCommand)

        if (output.contains("No such file or directory") || output.contains("Read-only file system") || output.contains(
                "Permission denied"
            )
        ) {
            // If 'ls' finds nothing it will fail; handle accordingly
            if (shellCommand.startsWith("ls")) {
                throw LsFailedException("ls failed or found nothing: $output")
            }
            throw Exception("ADB shell command failed: $output")
        }
        return output
    }

    class LsFailedException(message: String) : Exception(message)

    fun readDeviceFile(filePath: String): String {
        // Use "cat" to read device files
        return runAdbShell("cat $filePath")
    }

    fun findHwmonNodes(hwPath: String, pattern: String): List<String> {
        return try {
            val lsOutput = runAdbShell("ls -1 $hwPath/$pattern")

            lsOutput.lines()
                .filter { it.isNotBlank() }
                .map { it.split('/').last() } // extract 'power1_input'
                .sorted() // sort alphabetically (in0, in1, in2...)
        } catch (e: LsFailedException) {
            // 'ls' failing because nothing matched is an expected situation
            emptyList()
        }
    }

    /**
     * Extract channel index from names like "power1_input", "in0_label" -> 0, 1
     */
    fun extractIndexFromNode(nodeName: String): Int? {
        // "in1_label" -> "in1" -> "1" -> 1
        // "power1_input" -> "power1" -> "1" -> 1
        return nodeName.substringBefore("_").filter { it.isDigit() }.toIntOrNull()
    }

    fun run() {
        // *** Modification: get and format current time ***
        val currentDateTime = ZonedDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
        val formattedDateTime = currentDateTime.format(formatter)
        val outputReport = StringBuilder()

        outputReport.appendLine("# Power Report (auto-generated)")
        outputReport.appendLine("**Report time:** `$formattedDateTime`\n")
        outputReport.appendLine("--- Checking ADB device connection ---")

        val echoTest = runAdbShell("echo 'ADB connection OK'")
        if (!echoTest.contains("ADB connection OK")) {
            outputReport.appendLine("Error: ADB 'echo' test failed. Output: $echoTest")
            println(outputReport.toString()) // print what we've captured so far
            exitProcess(1)
        }

        outputReport.appendLine("--- Scanning all hwmon directories for 'ina...' sensors and calculating power ---")
        val hwmonDirs: List<String>
        try {
            val lsOutput = runAdbShell("ls -d /sys/class/hwmon/hwmon*")
            hwmonDirs = lsOutput.lines().filter { it.isNotBlank() }

            if (hwmonDirs.isEmpty()) {
                outputReport.appendLine("Error: No /sys/class/hwmon/hwmon* directories found on device.")
                println(outputReport.toString())
                exitProcess(1)
            }

        } catch (e: Exception) {
            outputReport.appendLine("Error: Unable to list hwmon directories on device: ${e.message}")
            println(outputReport.toString())
            exitProcess(1)
        }

        outputReport.appendLine("Found ${hwmonDirs.size} hwmon directories on device, checking...")
        outputReport.appendLine("====================================================")

        var foundSensorCount = 0
        for (hwPath in hwmonDirs) {
            val currentHwPath = hwPath.trim()
            val namePath = "$currentHwPath/name"

            val sensorName: String
            try {
                sensorName = readDeviceFile(namePath).trim()
            } catch (e: Exception) {
                outputReport.appendLine("⚪️ Skipping $currentHwPath (cannot read 'name' file)")
                continue
            }

            // Check if it's an 'ina' sensor
            if (sensorName.startsWith("ina")) {
                foundSensorCount++
                outputReport.appendLine("\n### ✅ $currentHwPath (type: $sensorName)\n")

                // --- Collect all channel data (V, I, P, and Label) ---
                val channels = mutableMapOf<Int, ChannelData>()
                fun getChannel(index: Int) = channels.getOrPut(index) { ChannelData() }

                // 1. Find and read all label nodes
                findHwmonNodes(currentHwPath, "*_label").forEach { node ->
                    extractIndexFromNode(node)?.let { index ->
                        try {
                            val rawValue = readDeviceFile("$currentHwPath/$node")
                            getChannel(index).label = rawValue.trim()
                            getChannel(index).labelNode = node // record node name
                        } catch (e: Exception) { /* ignore read failures */ }
                    }
                }

                // 2. Find and read all power nodes
                findHwmonNodes(currentHwPath, "power*_input").forEach { node ->
                    extractIndexFromNode(node)?.let { index ->
                        try {
                            val rawValue = readDeviceFile("$currentHwPath/$node")
                            getChannel(index).powerUw = rawValue.toLongOrNull()
                            getChannel(index).powerNode = node // record node name
                        } catch (e: Exception) { /* ignore read failures */ }
                    }
                }

                // 3. Find and read all current nodes
                findHwmonNodes(currentHwPath, "curr*_input").forEach { node ->
                    extractIndexFromNode(node)?.let { index ->
                        try {
                            val rawValue = readDeviceFile("$currentHwPath/$node")
                            getChannel(index).currentMa = rawValue.toLongOrNull()
                            getChannel(index).currentNode = node // record node name
                        } catch (e: Exception) { /* ignore read failures */ }
                    }
                }

                // 4. Find and read all voltage nodes
                findHwmonNodes(currentHwPath, "in*_input").forEach { node ->
                    extractIndexFromNode(node)?.let { index ->
                        try {
                            val rawValue = readDeviceFile("$currentHwPath/$node")
                            getChannel(index).voltageMv = rawValue.toLongOrNull()
                            getChannel(index).voltageNode = node // record node name
                        } catch (e: Exception) { /* ignore read failures */ }
                    }
                }

                val labeledChannels = channels.filter { it.value.label != null }
                val hasLabels = labeledChannels.isNotEmpty()

                val channelsToReport = if (hasLabels) {
                    labeledChannels.toSortedMap()
                } else {
                    channels.toSortedMap()
                }

                var totalPowerW = 0.0

                // Dynamic table header
                if (hasLabels) {
                    // 6-column header (with "Label")
                    outputReport.appendLine("| Channel | Label | Voltage (V) | Current (A) | Power (W) | Notes / Source (sysfs) |")
                    outputReport.appendLine("| :--- | :--- | :--- | :--- | :--- | :--- |")
                } else {
                    // 5-column header (no "Label")
                    outputReport.appendLine("| Channel | Voltage (V) | Current (A) | Power (W) | Notes / Source (sysfs) |")
                    outputReport.appendLine("| :--- | :--- | :--- | :--- | :--- |")
                }

                if (channelsToReport.isEmpty()) {
                    if (hasLabels) {
                        outputReport.appendLine("| N/A | (No channels with *_label found) | | | | |")
                    } else {
                        outputReport.appendLine("| N/A | (No V/I/P channels found) | | | |")
                    }
                }

                // --- Iterate and print rows ---
                for ((index, data) in channelsToReport) {
                    val voltageV = data.voltageMv?.let { it / 1000.0 }
                    val currentA = data.currentMa?.let { it / 1000.0 }
                    val directPowerW = data.powerUw?.let { it / 1_000_000.0 }

                    var finalPowerW: Double? = null
                    var notes = "" // notes (Calculated, Direct, ...)
                    val sources = mutableListOf<String>() // source paths

                    // Always add all valid nodes found
                    data.labelNode?.let { sources.add("`$currentHwPath/$it`") }
                    data.powerNode?.let { sources.add("`$currentHwPath/$it`") }
                    data.voltageNode?.let { sources.add("`$currentHwPath/$it`") }
                    data.currentNode?.let { sources.add("`$currentHwPath/$it`") }

                    if (directPowerW != null) {
                        finalPowerW = directPowerW
                        notes = "Direct"
                    } else if (voltageV != null && currentA != null) {
                        finalPowerW = voltageV * currentA
                        notes = "Calculated"
                    } else {
                        notes = "Incomplete data"
                    }

                    if (finalPowerW != null) {
                        totalPowerW += finalPowerW
                    }

                    val vStr = voltageV?.let { "%.3f".format(it) } ?: "N/A"
                    val aStr = currentA?.let { "%.3f".format(it) } ?: "N/A"
                    val pStr = finalPowerW?.let { "%.3f".format(it) } ?: "N/A"

                    val fullNotes = if (sources.isNotEmpty()) {
                        "$notes <br> ${sources.joinToString("<br>")}"
                    } else {
                        notes
                    }

                    // Dynamic row printing
                    if (hasLabels) {
                        val labelStr = data.label!! // safe because hasLabels == true
                        outputReport.appendLine("| $index | $labelStr | $vStr | $aStr | $pStr | $fullNotes |")
                    } else {
                        outputReport.appendLine("| $index | $vStr | $aStr | $pStr | $fullNotes |")
                    }
                }

                // Dynamic totals
                if (channelsToReport.isNotEmpty()) {
                    if (hasLabels) {
                        outputReport.appendLine("| **Total (labeled channels)** | | | | **%.3f** | |".format(totalPowerW))
                    } else {
                        outputReport.appendLine("| **Total** | | | **%.3f** | |".format(totalPowerW))
                    }
                }
                outputReport.appendLine("\n----------------------------------------------------")

            } else {
                outputReport.appendLine("⚪️ Skipping $currentHwPath (sensor type: '$sensorName')")
            }
        }

        outputReport.appendLine("====================================================")
        if (foundSensorCount == 0) {
            outputReport.appendLine("Scan complete. No 'ina...' sensors found in any hwmon directories.")
        } else {
            outputReport.appendLine("Scan complete. Found $foundSensorCount 'ina...' power sensor(s).")
        }
        outputReport.appendLine("====================================================")

        val reportString = outputReport.toString()

        // Print full report to console
        println(reportString)

        // Write full report to file
        try {
            File(OUTPUT_FILENAME).writeText(reportString)
            println("✅ Report successfully saved to: $OUTPUT_FILENAME")
        } catch (e: Exception) {
            println("❌ Error: Unable to save report to $OUTPUT_FILENAME")
            println("Reason: ${e.message}")
        }
    }
}
