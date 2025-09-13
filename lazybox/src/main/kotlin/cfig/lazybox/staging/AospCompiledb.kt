package cfig.lazybox.staging

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import java.io.FileInputStream
import java.util.regex.Pattern
import java.util.zip.GZIPInputStream

class AospCompiledb {
    data class CompileCommand(
        val directory: String,
        val command: String,
        val file: String
    )

    fun findAndroidRoot(logFile: File): String {
        // Get absolute path of the log file
        val logAbsPath = logFile.absoluteFile

        // The log is in out/verbose.log.gz, so Android root is the parent of the "out" directory
        var currentDir = logAbsPath.parentFile // This should be "out" directory
        while (currentDir != null && currentDir.name != "out") {
            currentDir = currentDir.parentFile
        }

        return if (currentDir != null) {
            // Go up one more level to get the Android root (parent of "out")
            currentDir.parentFile?.absolutePath ?: System.getProperty("user.dir")
        } else {
            // Fallback: try to find Android root by looking for typical Android files
            var dir = logAbsPath.parentFile
            while (dir != null) {
                if (File(dir, "build/make").exists() || File(dir, "Makefile").exists() || File(dir, "build.gradle").exists()) {
                    return dir.absolutePath
                }
                dir = dir.parentFile
            }
            System.getProperty("user.dir")
        }
    }

    fun parseVerboseLog(gzFile: File, androidRoot: String): List<CompileCommand> {
        val compileCommands = mutableListOf<CompileCommand>()
        val ninjaCommandPattern = Pattern.compile("""^\[(\d+)/(\d+)\]\s+(.+)$""")

        GZIPInputStream(FileInputStream(gzFile)).bufferedReader().use { reader ->
            reader.lineSequence().forEach { line ->
                val matcher = ninjaCommandPattern.matcher(line)
                if (matcher.matches()) {
                    val command = matcher.group(3)

                    // Only process compilation commands (with -c flag), not linking commands
                    if (command.contains(" -c ") && command.contains("clang")) {
                        val compileCommand = parseCompilationCommand(command, androidRoot)
                        if (compileCommand != null) {
                            compileCommands.add(compileCommand)
                        }
                    }
                }
            }
        }

        return compileCommands
    }

    fun parseCompilationCommand(commandLine: String, androidRoot: String): CompileCommand? {
        try {
            // Parse the command to extract compiler path, flags, and source file
            val parts = splitCommandLine(commandLine)
            if (parts.isEmpty()) return null

            // Find the compiler executable
            val compilerIndex = parts.indexOfFirst { it.contains("clang") }
            if (compilerIndex == -1) return null

            // Find source files (typically .c, .cpp, .cc files that are not output files)
            val sourceFiles = findSourceFiles(parts)
            if (sourceFiles.isEmpty()) return null

            // Build the clean command (without PWD prefix)
            val cleanCommand = buildCleanCommand(parts, compilerIndex)

            // Create compile command for each source file
            val sourceFile = sourceFiles.first() // Take the first source file

            return CompileCommand(
                directory = androidRoot,
                command = cleanCommand,
                file = sourceFile
            )
        } catch (e: Exception) {
            println("Warning: Failed to parse command: ${e.message}")
            return null
        }
    }


    fun splitCommandLine(commandLine: String): List<String> {
        // Remove PWD= prefix if present
        val cleanCommand = commandLine.replace(Regex("""PWD=[^\s]+\s*"""), "")

        // Simple command line splitting (handles basic quoting)
        val parts = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        var escapeNext = false

        for (char in cleanCommand) {
            when {
                escapeNext -> {
                    current.append(char)
                    escapeNext = false
                }
                char == '\\' -> {
                    escapeNext = true
                }
                char == '"' -> {
                    inQuotes = !inQuotes
                }
                char == ' ' && !inQuotes -> {
                    if (current.isNotEmpty()) {
                        parts.add(current.toString())
                        current = StringBuilder()
                    }
                }
                else -> {
                    current.append(char)
                }
            }
        }

        if (current.isNotEmpty()) {
            parts.add(current.toString())
        }

        return parts
    }

    fun findSourceFiles(parts: List<String>): List<String> {
        val sourceExtensions = setOf(".c", ".cpp", ".cc", ".cxx", ".c++")
        return parts.filter { part ->
            sourceExtensions.any { ext -> part.endsWith(ext) } &&
                    !part.startsWith("-") && // Not a flag
                    !part.contains("crtbegin") && // Not a crt file
                    !part.contains("crtend") &&
                    File(part).extension in sourceExtensions.map { it.substring(1) }
        }
    }

    fun buildCleanCommand(parts: List<String>, compilerIndex: Int): String {
        // Join all parts starting from the compiler
        return parts.drop(compilerIndex).joinToString(" ")
    }

    fun run() {
        val logFile = File("out/verbose.log.gz")
        val outputFile = File("compile_commands.json")

        if (!logFile.exists()) {
            println("Error: verbose.log.gz not found in out/ directory")
            return
        }

        // Find Android root directory from verbose log location
        val androidRoot = findAndroidRoot(logFile)
        println("Android root directory: $androidRoot")

        println("Parsing verbose build log...")
        val compileCommands = parseVerboseLog(logFile, androidRoot)

        println("Found ${compileCommands.size} compilation commands")

        // Generate JSON
        val json = ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(compileCommands)

        outputFile.writeText(json)
        println("Generated compile_commands.json with ${compileCommands.size} entries")

    }
}
