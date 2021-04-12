package cfig.bootimg.cpio

import cfig.helper.Helper
import cfig.EnvironmentVerifier
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream
import org.apache.commons.compress.archivers.cpio.CpioConstants
import org.slf4j.LoggerFactory
import java.io.*
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Paths
import java.util.regex.Pattern

class AndroidCpio {
    private var inodeNumber: Long = 0L
    private val fsConfig: MutableSet<AndroidCpioEntry> = mutableSetOf()
    private val fsConfigTemplate: List<FsConfigTemplate> = loadFsConfigTemplate()

    data class FsConfigTemplate(
        var type: String = "REG",
        var mode: String = "",
        var uid: Int = 0,
        var gid: Int = 0,
        var prefix: String = ""
    )

    private fun packItem(root: File, item: File, outStream: OutputStream) {
        if ((item.path == root.path) && !Files.isDirectory(item.toPath())) {
            throw IllegalArgumentException("root path is not dir")
        }

        if (item.path == root.path) { //first visit to root
            val fileList = item.listFiles()?.apply { sortBy { it.name } }
            if (fileList != null) {
                for (subItem in fileList) {
                    packItem(root, subItem, outStream)
                }
            }
        } else { //later visit
            val newOutname = if (EnvironmentVerifier().isWindows) {
                item.path.substring(root.path.length + 1).replace("\\", "/")
            } else {
                item.path.substring(root.path.length + 1) //remove leading slash
            }
            log.debug(item.path + " ~ " + root.path + " => " + newOutname)
            val entry = when {
                Files.isSymbolicLink(item.toPath()) -> {
                    val target = Files.readSymbolicLink(Paths.get(item.path))
                    log.debug("LNK: " + item.path + " --> " + target)
                    AndroidCpioEntry(
                        name = newOutname,
                        statMode = java.lang.Long.valueOf("120644", 8),
                        data = target.toString().toByteArray(),
                        ino = inodeNumber++
                    )
                }
                Files.isDirectory(item.toPath()) -> {
                    log.debug("DIR: " + item.path + ", " + item.toPath())
                    AndroidCpioEntry(
                        name = newOutname,
                        statMode = java.lang.Long.valueOf("40755", 8),
                        data = byteArrayOf(),
                        ino = inodeNumber++
                    )
                }
                Files.isRegularFile(item.toPath()) -> {
                    log.debug("REG: " + item.path)
                    AndroidCpioEntry(
                        name = newOutname,
                        statMode = java.lang.Long.valueOf("100644", 8),
                        data = item.readBytes(),
                        ino = inodeNumber++
                    )
                }
                else -> {
                    throw IllegalArgumentException("do not support file " + item.name)
                }
            }
            log.debug("_eject: " + item.path)
            //fix_stat
            fixStat(entry)
            outStream.write(entry.encode())
            if (Files.isDirectory(item.toPath()) && !Files.isSymbolicLink(item.toPath())) {
                val fileList = item.listFiles()?.apply { sortBy { it.name } }
                if (fileList != null) {
                    for (subItem in fileList) {
                        packItem(root, subItem, outStream)
                    }
                }
            }
        }
    }

    private fun fnmatch(fileName: String, pattern: String): Boolean {
        return if (fileName == pattern) {
            true
        } else {
            Pattern.compile(
                "^" + pattern.replace(".", "\\.")
                    .replace("/", "\\/")
                    .replace("*", ".*") + "$"
            ).matcher(fileName).find()
        }
    }

    private fun fixStat(entry: AndroidCpioEntry) {
        val itemConfig = fsConfig.filter { it.name == entry.name }
        when (itemConfig.size) {
            0 -> { /* do nothing */
                val matches = fsConfigTemplate
                    .filter { fnmatch(entry.name, it.prefix) }
                    .sortedByDescending { it.prefix.length }
                if (matches.isNotEmpty()) {
                    val ftBits = NewAsciiCpio(c_mode = entry.statMode).let {
                        when {
                            it.isSymbolicLink() -> CpioConstants.C_ISLNK
                            it.isRegularFile() -> CpioConstants.C_ISREG
                            it.isDirectory() -> CpioConstants.C_ISDIR
                            else -> throw IllegalArgumentException("unsupported st_mode " + it.c_mode)
                        }
                    }
                    entry.statMode = ftBits.toLong() or java.lang.Long.valueOf(matches[0].mode, 8)
                    log.debug("${entry.name} ~ " + matches.map { it.prefix }.reduce { acc, s -> "$acc, $s" }
                            + ", stMode=" + java.lang.Long.toOctalString(entry.statMode))
                } else {
                    log.debug("${entry.name} has NO fsconfig/prefix match")
                }
            }
            1 -> {
                log.debug("${entry.name} == preset fsconfig")
                entry.statMode = itemConfig[0].statMode
            }
            else -> {
                throw IllegalArgumentException("${entry.name} as multiple exact-match fsConfig")
            }
        }
    }

    fun pack(inDir: String, outFile: String, propFile: String? = null) {
        inodeNumber = 300000L
        fsConfig.clear()
        propFile?.let {
            if (File(propFile).exists()) {
                File(propFile).readLines().forEach { line ->
                    fsConfig.add(ObjectMapper().readValue(line, AndroidCpioEntry::class.java))
                }
            } else {
                log.warn("fsConfig file has been deleted, using fsConfig prefix matcher")
            }
        }
        FileOutputStream(outFile).use { fos ->
            packItem(File(inDir), File(inDir), fos)
            val trailer = AndroidCpioEntry(
                name = "TRAILER!!!",
                statMode = java.lang.Long.valueOf("0755", 8),
                ino = inodeNumber++
            )
            fixStat(trailer)
            fos.write(trailer.encode())
        }
        val len = File(outFile).length()
        val rounded = Helper.round_to_multiple(len, 256) //file in page 256
        if (len != rounded) {
            FileOutputStream(outFile, true).use { fos ->
                fos.write(ByteArray((rounded - len).toInt()))
            }
        }
    }

    private fun loadFsConfigTemplate(): List<FsConfigTemplate> {
        val reader =
            BufferedReader(InputStreamReader(AndroidCpio::class.java.classLoader.getResourceAsStream("fsconfig.txt")!!))
        val oM = ObjectMapper().apply {
            configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true)
        }
        return reader.readLines().map { oM.readValue(it, FsConfigTemplate::class.java) }
    }

    companion object {
        private val log = LoggerFactory.getLogger(AndroidCpio::class.java)
        private val PERM_MASK = java.lang.Long.valueOf("777", 8)
        fun decompressCPIO(cpioFile: String, outDir: String, fileList: String? = null) {
            run { //clean up
                if (File(outDir).exists()) {
                    log.info("Cleaning $outDir ...")
                    File(outDir).deleteRecursively()
                }
                File(outDir).mkdir()
            }
            val cis = CpioArchiveInputStream(FileInputStream(cpioFile))
            val fileListDump = if (fileList != null) FileOutputStream(fileList) else null

            while (true) {
                val entry = cis.nextCPIOEntry ?: break
                val entryInfo = AndroidCpioEntry(
                    name = entry.name,
                    statMode = entry.mode,
                    ino = entry.inode,
                    note = String.format("%6s", java.lang.Long.toOctalString(entry.mode))
                )
                if (!cis.canReadEntryData(entry)) {
                    throw RuntimeException("can not read entry ??")
                }
                val buffer = ByteArray(entry.size.toInt())
                cis.read(buffer)
                val outEntryName = File(outDir + "/" + entry.name).path
                if (((entry.mode and PERM_MASK).shr(7)).toInt() != 0b11) {
                    //@formatter:off
                    log.warn("  root/${entry.name} has improper file mode "
                            + String.format("%03o, ", entry.mode and PERM_MASK) + "fix it"
                    )
                    //@formatter:on
                }
                when {
                    entry.isSymbolicLink -> {
                        entryInfo.note = ("LNK " + entryInfo.note)
                        if (EnvironmentVerifier().isWindows) {
                            File(outEntryName).writeBytes(buffer)
                        } else {
                            Files.createSymbolicLink(Paths.get(outEntryName), Paths.get(String(buffer)))
                        }
                    }
                    entry.isRegularFile -> {
                        entryInfo.note = ("REG " + entryInfo.note)
                        File(outEntryName).writeBytes(buffer)
                        if (EnvironmentVerifier().isWindows) {
                            //Windows: Posix not supported
                        } else {
                            Files.setPosixFilePermissions(
                                Paths.get(outEntryName),
                                Helper.modeToPermissions(((entry.mode and PERM_MASK) or 0b111_000_000).toInt())
                            )
                        }
                    }
                    entry.isDirectory -> {
                        entryInfo.note = ("DIR " + entryInfo.note)
                        File(outEntryName).mkdir()
                        if (!EnvironmentVerifier().isWindows) {
                            Files.setPosixFilePermissions(
                                Paths.get(outEntryName),
                                Helper.modeToPermissions(((entry.mode and PERM_MASK) or 0b111_000_000).toInt())
                            )
                        } else {
                            //Windows
                        }
                    }
                    else -> throw IllegalArgumentException("??? type unknown")
                }
                File(outEntryName).setLastModified(entry.time)
                log.debug(entryInfo.toString())
                fileListDump?.write(ObjectMapper().writeValueAsString(entryInfo).toByteArray())
                fileListDump?.write("\n".toByteArray())
            }
            val bytesRead = cis.bytesRead
            cis.close()
            val remaining = FileInputStream(cpioFile).use { fis ->
                fis.skip(bytesRead - 128)
                fis.readBytes()
            }
            val foundIndex = String(remaining, Charsets.UTF_8).lastIndexOf("070701")
            val entryInfo = AndroidCpioEntry(
                name = CpioConstants.CPIO_TRAILER,
                statMode = java.lang.Long.valueOf("755", 8)
            )
            if (foundIndex != -1) {
                val statusModeStr = String(remaining, Charsets.UTF_8).substring(foundIndex + 14, foundIndex + 22)
                entryInfo.statMode = java.lang.Long.valueOf(statusModeStr, 16)
                log.info("cpio trailer found, mode=$statusModeStr")
            } else {
                log.error("no cpio trailer found")
            }
            fileListDump?.write(ObjectMapper().writeValueAsString(entryInfo).toByteArray())
            fileListDump?.close()
        }
    }
}
