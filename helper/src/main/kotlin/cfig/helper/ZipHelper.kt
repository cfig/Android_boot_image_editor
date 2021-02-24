package cfig.helper

import cfig.helper.Helper.Companion.check_call
import cfig.helper.Helper.Companion.check_output
import cfig.io.Struct3
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.archivers.zip.ZipMethod
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipParameters
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.apache.commons.exec.PumpStreamHandler
import org.slf4j.LoggerFactory
import java.io.*
import java.lang.RuntimeException
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipException
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.jvm.isAccessible

class ZipHelper {
    class ZipEntryRecipe(val data: ByteArray, val name: String, val method: ZipMethod)

    companion object {
        private val log = LoggerFactory.getLogger("ZipHelper")

        /*
            unzip(): unzip fileName to outDir
         */
        fun unzip(fileName: String, outDir: String) {
            log.info("unzip: $fileName --> $outDir")
            if (File(outDir).exists()) {
                if (!File(outDir).isDirectory) {
                    throw RuntimeException("$outDir exists but is not directory")
                }
            } else {
                log.info("Creating $outDir ...")
                File(outDir).mkdirs()
            }
            ZipArchiveInputStream(FileInputStream(fileName)).use { zis ->
                while (true) {
                    val entry = zis.nextZipEntry ?: break
                    when {
                        entry.isDirectory -> {
                            val entryOut = File(outDir + "/" + entry.name)
                            entryOut.mkdir()
                            log.debug("Unzipping[d]: ${entry.name}")
                        }
                        entry.isUnixSymlink -> {
                            throw IllegalArgumentException("this should not happen: Found dir ${entry.name}")
                        }
                        else -> {
                            val entryOut = File(outDir + "/" + entry.name)
                            log.debug("Unzipping[f]: ${entry.name}")
                            FileOutputStream(entryOut).use {
                                zis.copyTo(it)
                            }
                        }
                    }
                }
            }
            log.info("unzip: $fileName --> $outDir Done.")
        }

        fun zipDelete(zipFile: File, entryName: String) {
            val zipProperties = mutableMapOf("create" to "false")
            val zipURI = URI.create("jar:file:" + zipFile.canonicalPath)
            FileSystems.newFileSystem(zipURI, zipProperties).use { zipfs ->
                val entryPath = zipfs.getPath(entryName)
                log.info("deleting " + entryPath.toUri() + " from ZIP File ${zipFile.name}")
                Files.delete(entryPath)
            }
        }

        fun zipClone(inFile: String, outFile: String) {
            ZipFile(inFile).use { zf ->
                val zaos = ZipArchiveOutputStream(FileOutputStream(outFile))
                val e = zf.entries
                while (e.hasMoreElements()) {
                    val entry = e.nextElement()
                    zaos.putArchiveEntry(entry)
                    zf.getInputStream(entry).copyTo(zaos)
                    zaos.closeArchiveEntry()
                }
                zaos.finish()
                zaos.close()
            }
        }

        fun zipEdit(inFile: String, entryRecipe: ZipEntryRecipe) {
            val tmpFile = File.createTempFile("edit.", ".zip")
            log.info("transforming $inFile --> $tmpFile ...")
            ZipFile(inFile).use { zf ->
                val zaos = ZipArchiveOutputStream(tmpFile)
                val e = zf.entries
                if (zf.getEntry(entryRecipe.name) == null) {
                    log.info("adding new entry [${entryRecipe.name}(${entryRecipe.method})] into [${tmpFile.canonicalPath}]")
                    val entry = ZipArchiveEntry(entryRecipe.name)
                    entry.method = entryRecipe.method.ordinal
                    zaos.putArchiveEntry(entry)
                    ByteArrayInputStream(entryRecipe.data).copyTo(zaos)
                }

                while (e.hasMoreElements()) {
                    val entry = e.nextElement()
                    zaos.putArchiveEntry(entry)
                    if (entry.name == entryRecipe.name) {
                        log.info("modifying existent entry [${entryRecipe.name}(${entryRecipe.method})] into [${tmpFile.canonicalPath}]")
                        ByteArrayInputStream(entryRecipe.data).copyTo(zaos)
                    } else {
                        log.debug("cloning entry ${entry.name} ...")
                        zf.getInputStream(entry).copyTo(zaos)
                    }
                    zaos.closeArchiveEntry()
                }

                zaos.finish()
                zaos.close()
            }
            log.info("transforming $inFile --> ${tmpFile.name} done")
            Files.move(tmpFile.toPath(), File(inFile).toPath(), StandardCopyOption.REPLACE_EXISTING)
            log.info("renaming ${tmpFile.canonicalPath} --> $inFile done")
        }


        /*
            https://github.com/python/cpython/blob/3.8/Lib/zipfile.py
            The "local file header" structure, magic number, size, and indices
            (section V.A in the format document)
            structFileHeader = "<4s2B4HL2L2H"
            stringFileHeader = b"PK\003\004"
            sizeFileHeader = struct.calcsize(structFileHeader)
        */
        fun ZipArchiveEntry.getEntryOffset(): Long {
            val zipFileHeaderSize = Struct3("<4s2B4HL2L2H").calcSize()
            val funGetLocalHeaderOffset = ZipArchiveEntry::class.declaredFunctions.filter { funcItem ->
                funcItem.name == "getLocalHeaderOffset"
            }[0]
            funGetLocalHeaderOffset.isAccessible = true
            val headerOffset = funGetLocalHeaderOffset.call(this) as Long
            val offset: Long = headerOffset + zipFileHeaderSize + this.localFileDataExtra.size + this.name.length
            log.debug("headerOffset = $headerOffset")
            log.debug("calcSize: $zipFileHeaderSize")
            return offset
        }

        fun ZipFile.dumpEntry(entryName: String, outFile: File, ignoreError: Boolean = false) {
            val entry = this.getEntry(entryName)
            if (entry != null) {
                log.info("dumping entry: $entryName -> $outFile")
                FileOutputStream(outFile).use { outStream ->
                    this.getInputStream(entry).copyTo(outStream)
                }
            } else {
                if (ignoreError) {
                    log.info("dumping entry: $entryName : entry not found, skip")
                } else {
                   throw IllegalArgumentException("$entryName doesn't exist")
                }
            }
        }

        fun ZipArchiveOutputStream.pack(
            inFile: File,
            entryName: String,
            zipMethod: ZipMethod = ZipMethod.DEFLATED
        ) {
            log.info("packing $entryName($zipMethod) from file $inFile (size=${inFile.length()} ...")
            val entry = ZipArchiveEntry(inFile, entryName)
            entry.method = zipMethod.ordinal
            this.putArchiveEntry(entry)
            Files.newInputStream(inFile.toPath()).copyTo(this)
            this.closeArchiveEntry()
        }

        fun ZipArchiveOutputStream.pack(
            inStream: InputStream,
            entryName: String,
            zipMethod: ZipMethod = ZipMethod.DEFLATED
        ) {
            log.info("packing $entryName($zipMethod) from input stream (size=unknown...")
            val entry = ZipArchiveEntry(entryName)
            entry.method = zipMethod.ordinal
            this.putArchiveEntry(entry)
            inStream.copyTo(this)
            this.closeArchiveEntry()
        }

        fun isXz(compressedFile: String): Boolean {
            return try {
                FileInputStream(compressedFile).use { fis ->
                    XZCompressorInputStream(fis).use {
                    }
                }
                true
            } catch (e: ZipException) {
                false
            }
        }

        fun isLz4(compressedFile: String): Boolean {
            return try {
                "lz4 -t $compressedFile".check_call()
                true
            } catch (e: Exception) {
                false
            }
        }

        fun lz4cat(lz4File: String, outFile: String) {
            "lz4 -d -fv $lz4File $outFile".check_call()
        }

        fun lz4(lz4File: String, inputStream: InputStream) {
            FileOutputStream(File(lz4File)).use { fos ->
                val baosE = ByteArrayOutputStream()
                DefaultExecutor().let { exec ->
                    exec.streamHandler = PumpStreamHandler(fos, baosE, inputStream)
                    val cmd = CommandLine.parse("lz4 -l -12")
                    if ("lz4 --version".check_output().contains("r\\d+,".toRegex())) {
                        log.warn("lz4 version obsolete, needs update")
                    } else {
                        cmd.addArgument("--favor-decSpeed")
                    }
                    log.info(cmd.toString())
                    exec.execute(cmd)
                }
                baosE.toByteArray().let {
                    if (it.isNotEmpty()) {
                        log.warn(String(it))
                    }
                }
            }
        }

        fun isGZ(compressedFile: String): Boolean {
            return try {
                FileInputStream(compressedFile).use { fis ->
                    GZIPInputStream(fis).use {
                    }
                }
                true
            } catch (e: ZipException) {
                false
            }
        }

        /*
            @function: zcat compressedFile > decompressedFile
         */
        fun zcat(compressedFile: String, decompressedFile: String) {
            FileInputStream(compressedFile).use { fileIn ->
                GZIPInputStream(fileIn).use { gZIPInputStream ->
                    FileOutputStream(decompressedFile).use { fileOutputStream ->
                        val buffer = ByteArray(1024)
                        while (true) {
                            val bytesRead = gZIPInputStream.read(buffer)
                            if (bytesRead <= 0) break
                            fileOutputStream.write(buffer, 0, bytesRead)
                        }
                        log.info("decompress(gz) done: $compressedFile -> $decompressedFile")
                    }
                }
            }
        }

        /*
            @function: gzip InputStream to file,
                       using java.util.zip.GZIPOutputStream

            caution: about gzip header - OS (Operating System)
            According to https://docs.oracle.com/javase/8/docs/api/java/util/zip/package-summary.html
            and GZIP spec RFC-1952(http://www.ietf.org/rfc/rfc1952.txt),
            gzip files created from java.util.zip.GZIPOutputStream will mark the OS field with
                0 - FAT filesystem (MS-DOS, OS/2, NT/Win32)
            But default image built from Android source code has the OS field:
                3 - Unix
         */
        fun gzip(compressedFile: String, fis: InputStream) {
            FileOutputStream(compressedFile).use { fos ->
                GZIPOutputStream(fos).use { gos ->
                    val buffer = ByteArray(1024)
                    while (true) {
                        val bytesRead = fis.read(buffer)
                        if (bytesRead <= 0) break
                        gos.write(buffer, 0, bytesRead)
                    }
                }
            }
            log.info("compress(gz) done: $compressedFile")
        }

        /*
            @function: gzip InputStream to file like Android minigzip,
                       using commons.compress GzipCompressorOutputStream,
            @dependency: commons.compress
         */
        fun minigzip(compressedFile: String, fis: InputStream) {
            val param = GzipParameters().apply {
                operatingSystem = 3 //3: Unix
            }
            FileOutputStream(compressedFile).use { fos ->
                GzipCompressorOutputStream(fos, param).use { gos ->
                    val buffer = ByteArray(1024)
                    while (true) {
                        val bytesRead = fis.read(buffer)
                        if (bytesRead <= 0) break
                        gos.write(buffer, 0, bytesRead)
                    }
                }
            }
            log.info("compress(gz) done: $compressedFile")
        }
    }
}
