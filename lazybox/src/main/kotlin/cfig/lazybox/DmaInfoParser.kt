package cfig.lazybox

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileNotFoundException

data class DmaBufInfo(
    val size: Long,
    val flags: String,
    val mode: String,
    val count: Int,
    val exp_name: String,
    val ino: String,
    val pid: Int?,
    val tids: List<Int>,
    val processName: String?,
    val attachedDevices: List<String>
)

class DmaInfoParser {

    companion object {
        private val log = LoggerFactory.getLogger(DmaInfoParser::class.java)
    }

    fun parse(args: Array<String>) {
        if (args.isEmpty()) {
            log.error("Usage: Provide the path to the dmainfo file as an argument.")
            return
        }

        val filePath = args[0]
        log.info("Parsing file: {}", filePath)

        try {
            val dmaInfoList = parseFile(filePath)

            if (dmaInfoList.isNotEmpty()) {
                val mapper = ObjectMapper()
                val writer = mapper.writerWithDefaultPrettyPrinter()
                dmaInfoList.forEach { info ->
                    log.info("Parsed object:\n{}", writer.writeValueAsString(info))
                }
                log.info("--------------------------------------------------")
                log.info("Successfully parsed {} DMA buffer objects.", dmaInfoList.size)
            } else {
                log.warn("No valid DMA buffer objects were found in the file.")
            }
        } catch (e: FileNotFoundException) {
            log.error("File operation failed: {}", e.message)
        } catch (e: Exception) {
            log.error("An unexpected error occurred during parsing.", e)
        }
    }

    /**
     * Reads and parses a dmainfo file from the given path.
     *
     * @param filePath The path to the dmainfo file.
     * @return A list of [DmaBufInfo] objects, one for each entry in the file.
     * @throws FileNotFoundException if the file does not exist.
     */
    private fun parseFile(filePath: String): List<DmaBufInfo> {
        val file = File(filePath)
        if (!file.exists()) {
            throw FileNotFoundException("Error: File not found at '$filePath'")
        }

        val allLines = file.readLines()

        val firstDataLineIndex = allLines.indexOfFirst { line ->
            line.trim().matches(Regex("""^[0-9a-fA-F]{8}\s+.*"""))
        }

        if (firstDataLineIndex == -1) {
            log.warn("No data lines found in the file.")
            return emptyList()
        }

        val content = allLines.subList(firstDataLineIndex, allLines.size).joinToString("\n")

        val blocks = content.split(Regex("(\\r?\\n){2,}"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        return blocks.mapNotNull { parseBlock(it) }
    }

    /**
     * Parses a single block of text representing one DMA buffer object.
     */
    private fun parseBlock(block: String): DmaBufInfo? {
        val lines = block.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return null

        val mainLine = lines.first()
        val mainLineRegex = Regex("""^(\w+)\s+(\w+)\s+(\w+)\s+(\d+)\s+([\w-]+)\s+(\w+)\s*(.*)$""")
        val match = mainLineRegex.find(mainLine)
        if (match == null) {
            log.warn("Skipping malformed line that doesn't match expected format: \"{}\"", mainLine)
            return null
        }

        val (sizeStr, flagsStr, modeStr, countStr, expName, ino, processStr) = match.destructured

        var pid: Int? = null
        val tids = mutableListOf<Int>()
        var processName: String? = null

        if (processStr.isNotBlank()) {
            val processParts = processStr.trim().split(Regex("\\s+"))
            val nameParts = mutableListOf<String>()
            var pidFound = false

            processParts.forEach { part ->
                val num = part.toIntOrNull()
                if (num != null) {
                    if (!pidFound) {
                        pid = num
                        pidFound = true
                    } else {
                        tids.add(num)
                    }
                } else {
                    nameParts.add(part)
                }
            }

            if (nameParts.isNotEmpty()) {
                processName = nameParts.joinToString(" ")
            }
        }

        val attachedDevices = lines.drop(1)
            .dropWhile { !it.trim().equals("Attached Devices:", ignoreCase = true) }
            .drop(1)
            .map { it.trim() }
            .takeWhile { !it.trim().startsWith("Total", ignoreCase = true) }
            .filter { it.isNotEmpty() }

        return DmaBufInfo(
            size = sizeStr.toLongOrNull() ?: 0L,
            flags = "0x$flagsStr",
            mode = "0x$modeStr",
            count = countStr.toIntOrNull() ?: 0,
            exp_name = expName,
            ino = ino,
            pid = pid,
            tids = tids,
            processName = processName,
            attachedDevices = attachedDevices
        )
    }
}
