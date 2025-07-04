package cfig.lazybox.staging

import java.io.File
import java.io.ByteArrayOutputStream
import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.apache.commons.exec.ExecuteWatchdog
import org.apache.commons.exec.PumpStreamHandler
import org.slf4j.LoggerFactory

class RepoWorker {
    private val log = LoggerFactory.getLogger(RepoWorker::class.simpleName)
    fun lfsPullRepo(args: Array<String>) {
        val startPath = args.firstOrNull() ?: "."
        val dirList = getMatchRepositories(startPath, ::isLfsEnabled, "LFS Enabled")
        log.warn("Found ${dirList.size} repositories with LFS enabled.")
        dirList.forEach { repoDir ->
            val relativePath = repoDir.toRelativeString(File(startPath).canonicalFile).ifEmpty { "." }
            log.info("Pulling [$relativePath]...")
            pullLfsContent(repoDir)
        }
        log.info("✨ Scan and pull complete.")
    }

    fun unshallowRepo(args: Array<String>) {
        val startPath = args.firstOrNull() ?: "."
        val dirList = getMatchRepositories(startPath, ::isShallowClone, "Shallow Clone")
        log.warn("Found ${dirList.size} shallow repositories.")
        dirList.forEach { repoDir ->
            val relativePath = repoDir.toRelativeString(File(startPath).canonicalFile).ifEmpty { "." }
            log.info("Unshallowing [$relativePath]...")
            unshallowGit(repoDir)
        }
        log.info("✨ Scan and unshallow complete.")
    }

    private fun getMatchRepositories(
        startPath: String,
        checker: (File) -> Boolean,
        checkerName: String = "LFS Enabled"
    ): List<File> {
        val ret = mutableListOf<File>()
        val startDir = File(startPath).canonicalFile
        log.info("🔍 Finding Git repositories using 'repo forall' in: ${startDir.absolutePath}")
        val gitRepositories = findGitRepositoriesWithRepo(startDir)
        if (gitRepositories.isEmpty()) {
            log.info("No Git repositories found. Make sure you are running this in a directory managed by 'repo'.")
            return listOf<File>()
        }
        log.info("✅ Found ${gitRepositories.size} Git repositories. Now checking for $checkerName status ...")
        gitRepositories.forEach { repoDir ->
            val relativePath = repoDir.toRelativeString(startDir).ifEmpty { "." }
            if (checker(repoDir)) {
                log.info("Checking [$relativePath]...")
                log.info("  -> ✅ $checkerName")
                ret.add(repoDir)
            } else {
                //log.info("Checking [$relativePath]...")
                //log.info("  -> 🔴 LFS Not Enabled.")
            }
        }
        return ret
    }

    private fun pullLfsContent(repoDir: File) {
        try {
            val commandLine = CommandLine("git").also {
                it.addArgument("lfs")
                it.addArgument("pull")
            }
            DefaultExecutor().also {
                //it.watchdog = ExecuteWatchdog(600000)
                it.streamHandler = PumpStreamHandler(System.out, System.err)
                it.workingDirectory = repoDir
                it.setExitValue(0)
                it.execute(commandLine)
            }
            log.info("  -> ✅ 'git lfs pull' completed successfully.")
        } catch (e: Exception) {
            log.error("  -> ❌ 'git lfs pull' failed for ${repoDir.name}: ${e.message}")
        }
    }

    private fun unshallowGit(repoDir: File) {
        try {
            val commandLine = CommandLine("git").also {
                it.addArgument("fetch")
                it.addArgument("--progress")
                it.addArgument("--unshallow")
            }
            DefaultExecutor().also {
                //it.watchdog = ExecuteWatchdog(180000)
                it.streamHandler = PumpStreamHandler(System.out, System.err)
                it.workingDirectory = repoDir
                it.setExitValue(0)
                it.execute(commandLine)
            }
            log.info("  -> ✅ 'git fetch --unshallow' completed successfully.")
        } catch (e: Exception) {
            log.error("  -> ❌ 'git fetch --unshallow' failed for ${repoDir.name}: ${e.message}")
        }
    }

    private fun findGitRepositoriesWithRepo(workingDir: File): List<File> {
        return try {
            val commandLine = CommandLine("repo").also {
                it.addArgument("forall")
                it.addArgument("-c")
                it.addArgument("pwd")
            }
            val outputStream = ByteArrayOutputStream()
            DefaultExecutor().also {
                it.watchdog = ExecuteWatchdog(60000) // 60 seconds
                it.streamHandler = PumpStreamHandler(outputStream)
                it.workingDirectory = workingDir
                it.setExitValue(0)
                it.execute(commandLine)
            }
            val output = outputStream.toString().trim()
            if (output.isEmpty()) {
                emptyList()
            } else {
                output.split(System.lineSeparator())
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .map { File(it) }
            }
        } catch (e: Exception) {
            log.error("Error executing 'repo forall': ${e.message}")
            log.error("Please ensure the 'repo' tool is installed and you are in a valid repo workspace.")
            emptyList()
        }
    }

    private fun isLfsEnabled(repoDir: File): Boolean {
        return try {
            val commandLine = CommandLine("git").also {
                it.addArgument("lfs")
                it.addArgument("track")
            }
            val outputStream = ByteArrayOutputStream()
            DefaultExecutor().also {
                it.watchdog = ExecuteWatchdog(5000)
                it.streamHandler = PumpStreamHandler(outputStream)
                it.workingDirectory = repoDir
                it.setExitValue(0)
                it.execute(commandLine)
            }
            val output = outputStream.toString()
            val lines = output.lines().filter { it.isNotBlank() }
            return lines.size > 1
        } catch (e: Exception) {
            false
        }
    }

    fun isShallowClone(repoDir: File): Boolean {
        if (!repoDir.isDirectory) {
            return false
        }
        return try {
            val commandLine = CommandLine("git").also {
                it.addArgument("rev-parse")
                it.addArgument("--is-shallow-repository")
            }
            val outputStream = ByteArrayOutputStream()
            val executor = DefaultExecutor().also {
                it.watchdog = ExecuteWatchdog(5000)
                it.streamHandler = PumpStreamHandler(outputStream)
                it.workingDirectory = repoDir
                it.setExitValue(0)
            }
            executor.execute(commandLine)
            val output = outputStream.toString().trim()
            return output.equals("true", ignoreCase = true)

        } catch (e: Exception) {
            false
        }
    }
}
