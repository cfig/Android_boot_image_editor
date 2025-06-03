import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: kotlin copyright_updater.kts <directory_or_file_path>")
        return
    }

    val path = args[0]
    val file = File(path)

    if (!file.exists()) {
        println("Error: Path '$path' does not exist")
        return
    }

    if (file.isFile()) {
        updateCopyrightForFile(file)
    } else if (file.isDirectory()) {
        updateCopyrightForDirectory(file)
    }
}

fun updateCopyrightForDirectory(directory: File) {
    println("Processing directory: ${directory.absolutePath}")

    directory.walkTopDown()
        .filter { it.isFile() && isSourceFile(it) }
        .forEach { file ->
            println("Processing file: ${file.relativeTo(directory)}")
            updateCopyrightForFile(file)
        }
}

fun updateCopyrightForFile(file: File) {
    try {
        val (firstCommitYear, lastCommitYear) = getCommitYears(file)
        if (firstCommitYear == null || lastCommitYear == null) {
            println("  Warning: Could not determine git history for ${file.name}")
            return
        }

        val content = file.readText()
        val updatedContent = updateCopyright(content, firstCommitYear, lastCommitYear)

        if (content != updatedContent) {
            file.writeText(updatedContent)
            println("  Updated copyright: $firstCommitYear-$lastCommitYear")
        } else {
            println("  No changes needed")
        }
    } catch (e: Exception) {
        println("  Error processing ${file.name}: ${e.message}")
    }
}

fun isSourceFile(file: File): Boolean {
    val sourceExtensions = setOf("kt", "java", "scala", "groovy", "js", "ts", "cpp", "c", "h", "hpp")
    return sourceExtensions.contains(file.extension.lowercase())
}

fun getCommitYears(file: File): Pair<Int?, Int?> {
    return try {
        // Get first commit year
        val firstCommitProcess = ProcessBuilder(
            "git", "log", "--reverse", "--format=%ci", "--", file.absolutePath
        ).directory(file.parentFile).start()

        val firstCommitOutput = firstCommitProcess.inputStream.bufferedReader().use { it.readText() }
        firstCommitProcess.waitFor(10, TimeUnit.SECONDS)

        // Get last commit year
        val lastCommitProcess = ProcessBuilder(
            "git", "log", "-1", "--format=%ci", "--", file.absolutePath
        ).directory(file.parentFile).start()

        val lastCommitOutput = lastCommitProcess.inputStream.bufferedReader().use { it.readText() }
        lastCommitProcess.waitFor(10, TimeUnit.SECONDS)

        val firstYear = extractYearFromGitDate(firstCommitOutput.lines().firstOrNull() ?: "")
        val lastYear = extractYearFromGitDate(lastCommitOutput.lines().firstOrNull() ?: "")

        Pair(firstYear, lastYear)
    } catch (e: Exception) {
        println("  Git command failed: ${e.message}")
        Pair(null, null)
    }
}

fun extractYearFromGitDate(dateString: String): Int? {
    return try {
        if (dateString.isBlank()) return null
        // Git date format: "2022-03-15 10:30:45 +0000"
        val year = dateString.substring(0, 4).toInt()
        year
    } catch (e: Exception) {
        null
    }
}

fun updateCopyright(content: String, firstYear: Int, lastYear: Int): String {
    val lines = content.lines().toMutableList()
    val copyrightLineIndex = findCopyrightLine(lines)

    return if (copyrightLineIndex != -1) {
        // Update existing copyright
        val updatedCopyrightLine = updateExistingCopyright(lines[copyrightLineIndex], firstYear, lastYear)
        lines[copyrightLineIndex] = updatedCopyrightLine
        lines.joinToString("\n")
    } else {
        // Add new copyright header
        val copyrightHeader = createCopyrightHeader(firstYear, lastYear)
        (copyrightHeader + lines).joinToString("\n")
    }
}

fun findCopyrightLine(lines: List<String>): Int {
    return lines.indexOfFirst { line ->
        line.contains("Copyright", ignoreCase = true) &&
        line.contains("yuyezhong@gmail.com", ignoreCase = true)
    }
}

fun updateExistingCopyright(copyrightLine: String, firstYear: Int, lastYear: Int): String {
    val yearRange = if (firstYear == lastYear) {
        firstYear.toString()
    } else {
        "$firstYear-$lastYear"
    }

    // Replace the year pattern in the existing copyright line
    return copyrightLine.replaceFirst(
        Regex("""\b\d{4}(-\d{4})?\b"""),
        yearRange
    )
}

fun createCopyrightHeader(firstYear: Int, lastYear: Int): List<String> {
    val yearRange = if (firstYear == lastYear) {
        firstYear.toString()
    } else {
        "$firstYear-$lastYear"
    }

    return listOf(
        "// Copyright $yearRange yuyezhong@gmail.com",
        "//",
        "// Licensed under the Apache License, Version 2.0 (the \"License\");",
        "// you may not use this file except in compliance with the License.",
        "// You may obtain a copy of the License at",
        "//",
        "//      http://www.apache.org/licenses/LICENSE-2.0",
        "//",
        "// Unless required by applicable law or agreed to in writing, software",
        "// distributed under the License is distributed on an \"AS IS\" BASIS,",
        "// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.",
        "// See the License for the specific language governing permissions and",
        "// limitations under the License.",
        ""
    )
}

main(args)
