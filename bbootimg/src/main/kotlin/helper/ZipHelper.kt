package cfig.helper

import cfig.bootimg.cpio.AndroidCpioEntry
import cfig.helper.Helper.Companion.check_call
import cfig.helper.Helper.Companion.check_output
import cfig.io.Struct3
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream
import org.apache.commons.compress.archivers.cpio.CpioConstants
import org.apache.commons.compress.archivers.zip.*
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipParameters
import org.apache.commons.compress.compressors.lz4.FramedLZ4CompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.utils.IOUtils
import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.apache.commons.exec.PumpStreamHandler
import org.slf4j.LoggerFactory
import java.io.*
import java.lang.IllegalArgumentException
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Paths
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

        fun unZipFile2(fileName: String, outDir: String) {
            val zis = ZipArchiveInputStream(BufferedInputStream(FileInputStream(fileName)))
            while (true) {
                val entry = zis.nextZipEntry ?: break
                val entryOut = File(outDir + "/" + entry.name)
                when {
                    entry.isDirectory -> {
                        log.error("Found dir : " + entry.name)
                        throw IllegalArgumentException("this should not happen")
                    }
                    entry.isUnixSymlink -> {
                        log.error("Found link: " + entry.name)
                        throw IllegalArgumentException("this should not happen")
                    }
                    else -> {
                        if (entry.name.contains("/")) {
                            log.debug("Createing dir: " + entryOut.parentFile.canonicalPath)
                            entryOut.parentFile.mkdirs()
                        }
                        log.info("Unzipping " + entry.name)
                        IOUtils.copy(zis, FileOutputStream(entryOut))
                    }
                }
            }
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

        fun dumpZipEntry(inFile: String, entryName: String, outFile: String) {
            log.info("dumping: $inFile#$entryName -> $outFile")
            val zf = ZipFile(inFile)
            val entry = zf.getEntry(entryName)
            FileOutputStream(outFile).use { outStream ->
                zf.getInputStream(entry).copyTo(outStream)
            }
            zf.close()
        }

        fun getEntryStream(zipFile: ZipFile, entryName: String): InputStream {
            return zipFile.getInputStream(zipFile.getEntry(entryName))
        }

        fun ZipFile.dumpEntryIfExists(entryName: String, outFile: File) {
            val entry = this.getEntry(entryName)
            if (entry != null) {
                log.info("dumping entry: $entryName -> $outFile")
                FileOutputStream(outFile).use { outStream ->
                    this.getInputStream(entry).copyTo(outStream)
                }
            } else {
                log.info("dumping entry: $entryName : entry not found, skip")
            }
        }

        fun ZipFile.dumpEntry(entryName: String, outFile: File) {
            log.info("dumping entry: $entryName -> $outFile")
            val entry = this.getEntry(entryName)
            FileOutputStream(outFile).use { outStream ->
                this.getInputStream(entry).copyTo(outStream)
            }
        }

        fun ZipFile.dumpEntry(entryName: String, outFile: String) {
            log.info("dumping entry: $entryName -> $outFile")
            val entry = this.getEntry(entryName)
            FileOutputStream(outFile).use { outStream ->
                this.getInputStream(entry).copyTo(outStream)
            }
        }

        fun ZipArchiveOutputStream.packFile(
            inFile: File,
            entryName: String,
            zipMethod: ZipMethod = ZipMethod.DEFLATED
        ) {
            log.info("packing $entryName($zipMethod) from file $inFile (size=${inFile.length()} ...")
            val entry = ZipArchiveEntry(inFile, entryName)
            entry.method = zipMethod.ordinal
            this.putArchiveEntry(entry)
            IOUtils.copy(Files.newInputStream(inFile.toPath()), this)
            this.closeArchiveEntry()
        }

        fun ZipArchiveOutputStream.packEntry(
            inBuf: ByteArray,
            entryName: String,
            zipMethod: ZipMethod = ZipMethod.DEFLATED
        ) {
            log.info("packing $entryName($zipMethod) from memory data (size=${inBuf.size}...")
            val entry = ZipArchiveEntry(entryName)
            entry.method = zipMethod.ordinal
            this.putArchiveEntry(entry)
            IOUtils.copy(ByteArrayInputStream(inBuf), this)
            this.closeArchiveEntry()
        }

        fun ZipArchiveOutputStream.packStream(
            inStream: InputStream,
            entryName: String,
            zipMethod: ZipMethod = ZipMethod.DEFLATED
        ) {
            log.info("packing $entryName($zipMethod) from input stream (size=unknown...")
            val entry = ZipArchiveEntry(entryName)
            entry.method = zipMethod.ordinal
            this.putArchiveEntry(entry)
            IOUtils.copy(inStream, this)
            this.closeArchiveEntry()
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
                    IOUtils.copy(zf.getInputStream(entry), zaos)
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
                    IOUtils.copy(ByteArrayInputStream(entryRecipe.data), zaos)
                }

                while (e.hasMoreElements()) {
                    val entry = e.nextElement()
                    zaos.putArchiveEntry(entry)
                    if (entry.name == entryRecipe.name) {
                        log.info("modifying existent entry [${entryRecipe.name}(${entryRecipe.method})] into [${tmpFile.canonicalPath}]")
                        IOUtils.copy(ByteArrayInputStream(entryRecipe.data), zaos)
                    } else {
                        log.debug("cloning entry ${entry.name} ...")
                        IOUtils.copy(zf.getInputStream(entry), zaos)
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

        fun isXZ(compressedFile: String): Boolean {
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

        fun isLZ4(compressedFile: String): Boolean {
            return try {
                "lz4 -t $compressedFile".check_call()
                true
            } catch (e: Exception) {
                false
            }
        }

        fun decompressLZ4Ext(lz4File: String, outFile: String) {
            "lz4 -d -fv $lz4File $outFile".check_call()
        }

        fun compressLZ4(lz4File: String, inputStream: InputStream) {
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

        fun decompressLZ4(framedLz4: String, outFile: String) {
            FramedLZ4CompressorInputStream(
                Files.newInputStream(Paths.get(framedLz4))
            ).use { zIn ->
                Files.newOutputStream(Paths.get(outFile)).use { out ->
                    log.info("decompress lz4: $framedLz4 -> $outFile")
                    val buffer = ByteArray(8192)
                    var n: Int
                    while (-1 != zIn.read(buffer).also { n = it }) {
                        out.write(buffer, 0, n)
                    }
                }
            }
        }

        @Throws(IOException::class)
        fun gnuZipFile(compressedFile: String, decompressedFile: String) {
            val buffer = ByteArray(1024)
            FileOutputStream(compressedFile).use { fos ->
                GZIPOutputStream(fos).use { gos ->
                    FileInputStream(decompressedFile).use { fis ->
                        var bytesRead: Int
                        while (true) {
                            bytesRead = fis.read(buffer)
                            if (bytesRead <= 0) break
                            gos.write(buffer, 0, bytesRead)
                        }
                        gos.finish()
                        log.info("gzip done: $decompressedFile -> $compressedFile")
                    }//file-input-stream
                }//gzip-output-stream
            }//file-output-stream
        }

        @Throws(IOException::class)
        fun unGnuzipFile(compressedFile: String, decompressedFile: String) {
            val buffer = ByteArray(1024)
            FileInputStream(compressedFile).use { fileIn ->
                //src
                GZIPInputStream(fileIn).use { gZIPInputStream ->
                    //src
                    FileOutputStream(decompressedFile).use { fileOutputStream ->
                        var bytesRead: Int
                        while (true) {
                            bytesRead = gZIPInputStream.read(buffer)
                            if (bytesRead <= 0) break
                            fileOutputStream.write(buffer, 0, bytesRead)
                        }
                        log.info("decompress(gz) done: $compressedFile -> $decompressedFile")
                    }
                }
            }
        }

        /*
            caution: about gzip header - OS (Operating System)

            According to https://docs.oracle.com/javase/8/docs/api/java/util/zip/package-summary.html and
            GZIP spec RFC-1952(http://www.ietf.org/rfc/rfc1952.txt), gzip files created from java.util.zip.GZIPOutputStream
            will mark the OS field with
                0 - FAT filesystem (MS-DOS, OS/2, NT/Win32)
            But default image built from Android source code has the OS field:
                3 - Unix
            This MAY not be a problem, at least we didn't find it till now.
         */
        @Throws(IOException::class)
        @Deprecated("this function misses features")
        fun gnuZipFile(compressedFile: String, fis: InputStream) {
            val buffer = ByteArray(1024)
            FileOutputStream(compressedFile).use { fos ->
                GZIPOutputStream(fos).use { gos ->
                    var bytesRead: Int
                    while (true) {
                        bytesRead = fis.read(buffer)
                        if (bytesRead <= 0) break
                        gos.write(buffer, 0, bytesRead)
                    }
                    log.info("compress(gz) done: $compressedFile")
                }
            }
        }

        fun gnuZipFile2(compressedFile: String, fis: InputStream) {
            val buffer = ByteArray(1024)
            val p = GzipParameters()
            p.operatingSystem = 3
            FileOutputStream(compressedFile).use { fos ->
                GzipCompressorOutputStream(fos, p).use { gos ->
                    var bytesRead: Int
                    while (true) {
                        bytesRead = fis.read(buffer)
                        if (bytesRead <= 0) break
                        gos.write(buffer, 0, bytesRead)
                    }
                    log.info("compress(gz) done: $compressedFile")
                }
            }
        }

    }
}
