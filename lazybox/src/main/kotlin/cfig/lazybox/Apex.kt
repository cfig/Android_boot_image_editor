package cfig.lazybox

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

private object Colors {
    const val RED = "\u001B[0;31m"
    const val GREEN = "\u001B[0;32m"
    const val YELLOW = "\u001B[1;33m"
    const val BLUE = "\u001B[0;34m"
    const val CYAN = "\u001B[0;36m"
    const val NC = "\u001B[0m"
}

private fun printHeader(message: String) {
    println("${Colors.BLUE}========================================${Colors.NC}")
    println("${Colors.BLUE}$message${Colors.NC}")
    println("${Colors.BLUE}========================================${Colors.NC}")
}

private fun printInfo(message: String) {
    println("${Colors.GREEN}[INFO]${Colors.NC} $message")
}

private fun printWarn(message: String) {
    println("${Colors.YELLOW}[WARN]${Colors.NC} $message")
}

private fun printError(message: String) {
    println("${Colors.RED}[ERROR]${Colors.NC} $message")
}

private fun printSuccess(message: String) {
    println("${Colors.GREEN}[OK]${Colors.NC} $message")
}

private data class CommandResult(val output: List<String>, val exitCode: Int)

private fun runHostCommand(vararg command: String): CommandResult {
    return try {
        val process = ProcessBuilder(*command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readLines()
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroy()
            CommandResult(output, 124)
        } else {
            CommandResult(output, process.exitValue())
        }
    } catch (e: Exception) {
        CommandResult(emptyList(), 127)
    }
}

private class AdbHelper {
    fun isAdbAvailable(): Boolean {
        return runHostCommand("adb", "version").exitCode == 0
    }

    fun isConnected(): Boolean {
        val devices = runHostCommand("adb", "devices").output
        return devices.any { line ->
            val trimmed = line.trim()
            trimmed.isNotEmpty() && trimmed.endsWith("device") && !trimmed.startsWith("List")
        }
    }

    fun executeOnDevice(command: String): List<String> {
        return runHostCommand("adb", "shell", command).output
    }

    fun findApexFiles(): List<String> {
        val apexFiles = mutableListOf<String>()
        apexFiles.addAll(executeOnDevice("find / -name '*.apex' 2>/dev/null"))
        apexFiles.addAll(executeOnDevice("find / -name '*.capex' 2>/dev/null"))
        return apexFiles.filter { it.isNotBlank() }
    }

    fun getApexMounts(): List<String> {
        return executeOnDevice("mount | grep apex").filter { it.isNotBlank() }
    }

    fun findPermissionController(): List<String> {
        return executeOnDevice("find / -name '*permission*.apex' 2>/dev/null").filter { it.isNotBlank() }
    }
}

class Apex {
    private val adb = AdbHelper()

    private fun checkConnection() {
        printHeader("Check ADB connection")

        if (!adb.isAdbAvailable()) {
            printError("ADB is not installed or not in PATH")
            exitProcess(1)
        }

        if (!adb.isConnected()) {
            printError("No connected devices found")
            println("Please ensure:")
            println("  1. Device connected via USB")
            println("  2. USB debugging enabled")
            println("  3. ADB authorization granted")
            exitProcess(1)
        }

        printSuccess("ADB connection OK")
        runHostCommand("adb", "devices").output.forEach { println(it) }
        println()
    }

    private fun findApexFiles() {
        printHeader("Search APEX files")

        printInfo("Searching APEX files on device...")
        val apexFiles = adb.findApexFiles()

        if (apexFiles.isEmpty()) {
            printWarn("No APEX files found")
            return
        }

        printSuccess("Found ${apexFiles.size} APEX files")
        println()

        printHeader("APEX file list")
        apexFiles.sorted().forEachIndexed { index, file ->
            println("${index + 1}. $file")
        }
        println()

        val apexCount = apexFiles.count { it.endsWith(".apex") }
        val capexCount = apexFiles.count { it.endsWith(".capex") }

        printHeader("Statistics")
        println("Total: ${apexFiles.size}")
        println("  - .apex files: $apexCount")
        println("  - .capex files: $capexCount")
        println()

        printHeader("Group by location")
        println()

        val byLocation = apexFiles.groupBy { file ->
            when {
                file.contains("/system/apex") -> "/system/apex"
                file.startsWith("/apex") -> "/apex"
                file.contains("/system/priv-app") -> "/system/priv-app"
                file.contains("/data") -> "/data"
                else -> "other"
            }
        }

        byLocation.forEach { (location, files) ->
            println("${Colors.CYAN}$location:${Colors.NC}")
            files.sorted().forEach { println("  $it") }
            println()
        }

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val outputFile = File("apex_files_$timeStamp.txt")
        outputFile.writeText(apexFiles.sorted().joinToString("\n"))
        printInfo("Saved to: ${outputFile.absolutePath}")
        println()
    }

    private fun checkMounts() {
        printHeader("Check APEX mounts")

        val mounts = adb.getApexMounts()
        if (mounts.isEmpty()) {
            printWarn("No mounted APEX found")
        } else {
            mounts.forEach { println(it) }
        }
        println()
    }

    private fun checkPermissionController() {
        printHeader("Check PermissionController")

        printInfo("Searching permission-related APEX...")
        val permApex = adb.findPermissionController()
        if (permApex.isEmpty()) {
            printWarn("No permission-related APEX found")
        } else {
            permApex.forEach { println(it) }
        }
        println()

        printInfo("Searching permission XML files...")
        val privappPerms = adb.executeOnDevice("find / -name 'privapp-permissions*.xml' 2>/dev/null")
            .filter { it.isNotBlank() }
        if (privappPerms.isEmpty()) {
            printWarn("No permission XML files found")
        } else {
            privappPerms.forEach { println(it) }
        }
        println()
    }

    private fun showHelp() {
        println(
            """
            Usage: lazybox apex [options]

            Options:
                -h, --help      Show help
                -a, --all       Run all checks
                -f, --find      Find APEX files (default)
                -m, --mounts    Check APEX mounts
                -p, --perm      Check PermissionController

            Examples:
                lazybox apex
                lazybox apex --all
                lazybox apex --mounts
            """.trimIndent()
        )
    }

    fun run(args: Array<String>) {
        when {
            args.isEmpty() || args[0] == "-f" || args[0] == "--find" -> {
                checkConnection()
                findApexFiles()
            }
            args[0] == "-h" || args[0] == "--help" -> {
                showHelp()
            }
            args[0] == "-a" || args[0] == "--all" -> {
                checkConnection()
                findApexFiles()
                checkMounts()
                checkPermissionController()
            }
            args[0] == "-m" || args[0] == "--mounts" -> {
                checkConnection()
                checkMounts()
            }
            args[0] == "-p" || args[0] == "--perm" -> {
                checkConnection()
                checkPermissionController()
            }
            else -> {
                println("Unknown option: ${args[0]}")
                showHelp()
            }
        }
    }
}
