// Copyright 2021 yuyezhong@gmail.com
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package cfig.helper

import cc.cfig.io.Struct
import cfig.helper.Helper.Companion.check_call
import cfig.helper.Helper.Companion.check_output
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.zip.*
import org.apache.commons.compress.compressors.CompressorOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipParameters
import org.apache.commons.compress.compressors.lzma.LZMACompressorInputStream
import org.apache.commons.compress.compressors.lzma.LZMACompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.apache.commons.exec.PumpStreamHandler
import org.slf4j.LoggerFactory
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZFormatException
import org.tukaani.xz.XZOutputStream
import java.io.*
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

    class XZCompressorOutputStream2 : CompressorOutputStream {
        private val out: XZOutputStream

        constructor(outputStream: OutputStream?) {
            out = XZOutputStream(outputStream, LZMA2Options())
        }

        constructor(outputStream: OutputStream?, checkType: Int) {
            out = XZOutputStream(outputStream, LZMA2Options(), checkType)
        }

        @Throws(IOException::class)
        override fun write(b: Int) {
            out.write(b)
        }

        @Throws(IOException::class)
        override fun write(buf: ByteArray, off: Int, len: Int) {
            out.write(buf, off, len)
        }

        @Throws(IOException::class)
        override fun flush() {
            out.flush()
        }

        @Throws(IOException::class)
        fun finish() {
            out.finish()
        }

        @Throws(IOException::class)
        override fun close() {
            out.close()
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger("ZipHelper")

        val isMacOS: Boolean
            get() = System.getProperty("os.name").contains("Mac")

        val isLinux: Boolean
            get() = System.getProperty("os.name").contains("Linux")

        val isWindows: Boolean
            get() = System.getProperty("os.name").contains("Windows")

        private var lz4prog = ""

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
                            entryOut.parentFile?.let {
                                if (!it.exists()) {
                                    log.info("create parent dir: " + it.path)
                                    it.mkdirs()
                                }
                            }
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
            val zipFileHeaderSize = Struct("<4s2B4HL2L2H").calcSize()
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

        /*
        LZMACompressorInputStream() may raise OOM sometimes, like this:
            Caused by: java.lang.OutOfMemoryError: Java heap space
            at org.tukaani.xz.ArrayCache.getByteArray(Unknown Source)
            at org.tukaani.xz.lz.LZDecoder.<init>(Unknown Source)
            at org.tukaani.xz.LZMAInputStream.initialize(Unknown Source)
        So we catch it explicitly.
         */
        fun isLzma(compressedFile: String): Boolean {
            return try {
                FileInputStream(compressedFile).use { fis ->
                    LZMACompressorInputStream(fis).use {
                    }
                }
                true
            } catch (e: IOException) {
                false
            } catch (e: OutOfMemoryError) {
                false
            }
        }

        fun lzma(compressedFile: String, fis: InputStream) {
            log.info("Compress(lzma) ... ")
            FileOutputStream(compressedFile).use { fos ->
                LZMACompressorOutputStream(fos).use { gos ->
                    val buffer = ByteArray(1024)
                    while (true) {
                        val bytesRead = fis.read(buffer)
                        if (bytesRead <= 0) break
                        gos.write(buffer, 0, bytesRead)
                    }
                }
            }
            log.info("compress(lzma) done: $compressedFile")
        }

        /*
            @function: lzcat compressedFile > decompressedFile
         */
        fun lzcat(compressedFile: String, decompressedFile: String) {
            FileInputStream(compressedFile).use { fileIn ->
                LZMACompressorInputStream(fileIn).use { lzmaInputStream ->
                    FileOutputStream(decompressedFile).use { fileOutputStream ->
                        val buffer = ByteArray(1024)
                        while (true) {
                            val bytesRead = lzmaInputStream.read(buffer)
                            if (bytesRead <= 0) break
                            fileOutputStream.write(buffer, 0, bytesRead)
                        }
                    }
                }
            }
            log.info("decompress(lzma) done: $compressedFile -> $decompressedFile")
        }

        // https://tukaani.org/xz/xz-file-format.txt
        // 2.1.1. Stream Header
        fun parseStreamFlagCheckType(file: String): Int {
            FileInputStream(file).use { fis ->
                val ba = ByteArray(6)
                check(fis.read(ba) == ba.size)
                check(ba.contentEquals(byteArrayOf(0xfd.toByte(), 0x37, 0x7a, 0x58, 0x5a, 0x00))) {
                    log.warn("wrong magic bytes in xz header")
                }
                check(fis.read(ba) == ba.size)
                check(ba[0] == 0x00.toByte())
                when (ba[1].toInt()) {
                    0x00 -> log.info("NONE")
                    0x01 -> log.info("CRC32")
                    0x04 -> log.info("CRC64")
                    0x0a -> log.info("SHA256")
                    else -> throw IllegalArgumentException(
                        "unsupported StreamFlag.CheckType: 0x" + ba[1].toInt().toString(16)
                    )
                }
                return ba[1].toInt()
            }
        }

        fun xzStreamFlagCheckTypeToString(type: Int): String {
            return when (type) {
                0x00 -> "NONE"
                0x01 -> "CRC32"
                0x04 -> "CRC64"
                0x0a -> "SHA256"
                else -> throw IllegalArgumentException(
                    "unsupported StreamFlag.CheckType: 0x" + type.toString(16)
                )
            }
        }

        fun xzStreamFlagCheckTypeFromString(typeStr: String): Int {
            return when (typeStr) {
                "NONE" -> 0x00
                "CRC32" -> 0x01
                "CRC64" -> 0x04
                "SHA256" -> 0x0a
                else -> throw IllegalArgumentException(
                    "unsupported StreamFlag.CheckType: 0x$typeStr"
                )
            }
        }

        fun isXz(compressedFile: String): Boolean {
            return try {
                FileInputStream(compressedFile).use { fis ->
                    XZCompressorInputStream(fis).use {
                    }
                }
                true
            } catch (e: XZFormatException) {
                false
            }
        }

        fun xz(compressedFile: String, fis: InputStream, checkType: String) {
            log.info("Compress(xz), with checkType $checkType... ")
            FileOutputStream(compressedFile).use { fos ->
                XZCompressorOutputStream2(fos, ZipHelper.xzStreamFlagCheckTypeFromString(checkType)).use { gos ->
                    val buffer = ByteArray(1024)
                    while (true) {
                        val bytesRead = fis.read(buffer)
                        if (bytesRead <= 0) break
                        gos.write(buffer, 0, bytesRead)
                    }
                }
            }
            log.info("compress(xz) done: $compressedFile")
        }

        /*
            @function: xzcat compressedFile > decompressedFile
         */
        fun xzcat(compressedFile: String, decompressedFile: String) {
            FileInputStream(compressedFile).use { fileIn ->
                XZCompressorInputStream(fileIn).use { zis ->
                    FileOutputStream(decompressedFile).use { fileOutputStream ->
                        val buffer = ByteArray(1024)
                        while (true) {
                            val bytesRead = zis.read(buffer)
                            if (bytesRead <= 0) break
                            fileOutputStream.write(buffer, 0, bytesRead)
                        }
                    }
                }
            }
            log.info("decompress(xz) done: $compressedFile -> $decompressedFile")
        }

        private fun getLz4Prog(): String {
            if (lz4prog.isBlank()) {
                lz4prog = "lz4"
                if (isWindows) {
                    try {
                        Runtime.getRuntime().exec(arrayOf("tools/bin/lz4.exe", "--version"), null, null)
                        lz4prog = "tools/bin/lz4.exe"
                    } catch (e: Exception) {
                        log.warn("lz4 not installed")
                    }
                }
            }
            return lz4prog
        }

        val hasLz4: Boolean
            get() : Boolean {
                try {
                    Runtime.getRuntime().exec(arrayOf(getLz4Prog(), "--version"), null, null)
                    log.debug("lz4 available")
                } catch (e: Exception) {
                    log.warn("lz4 not installed")
                    if (isMacOS) {
                        log.warn("For Mac OS: \n\n\tbrew install lz4\n")
                    }
                    return false
                }
                return true
            }

        fun isLz4(compressedFile: String): Boolean {
            return try {
                "${getLz4Prog()} -t $compressedFile".check_call()
                true
            } catch (e: Exception) {
                false
            }
        }

        fun lz4cat(lz4File: String, outFile: String) {
            "${getLz4Prog()} -d -fv $lz4File $outFile".check_call()
        }

        fun lz4(lz4File: String, inputStream: InputStream) {
            FileOutputStream(File(lz4File)).use { fos ->
                val baosE = ByteArrayOutputStream()
                DefaultExecutor().let { exec ->
                    exec.streamHandler = PumpStreamHandler(fos, baosE, inputStream)
                    // -l: compress using Legacy format (Linux kernel compression)
                    // -12: --best
                    // --favor-decSpeed: compressed files decompress faster, but are less compressed
                    val cmd = CommandLine.parse("${getLz4Prog()} -l -12")
                    if ("${getLz4Prog()} --version".check_output().contains("r\\d+,".toRegex())) {
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

        fun isAndroidCpio(compressedFile: String): Boolean {
            return Dumpling(compressedFile).readFully(0L..5)
                .contentEquals(byteArrayOf(0x30, 0x37, 0x30, 0x37, 0x30, 0x31))
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
                    }
                }
            }
            log.info("decompress(gz) done: $compressedFile -> $decompressedFile")
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

        fun isBzip2(compressedFile: String): Boolean {
            return try {
                FileInputStream(compressedFile).use { fis ->
                    BZip2CompressorInputStream(fis).use { }
                }
                true
            } catch (e: IOException) {
                false
            }
        }

        fun bzip2(compressedFile: String, fis: InputStream) {
            log.info("Compress(bzip2) ... ")
            FileOutputStream(compressedFile).use { fos ->
                BZip2CompressorOutputStream(fos).use { zos ->
                    val buffer = ByteArray(1024)
                    while (true) {
                        val bytesRead = fis.read(buffer)
                        if (bytesRead <= 0) break
                        zos.write(buffer, 0, bytesRead)
                    }
                }
            }
            log.info("compress(bzip2) done: $compressedFile")
        }

        fun makeTar(compressedFile: String, srcDir: String) {
            FileOutputStream(compressedFile).use { fos ->
                val appendTarEntry: (TarArchiveOutputStream, String) -> Unit = { tarOut, entry ->
                    tarOut.putArchiveEntry(TarArchiveEntry(File(entry)))
                    tarOut.write(File(entry).readBytes())
                    tarOut.closeArchiveEntry()
                }
                TarArchiveOutputStream(fos).use { to ->
                    Files.walk(Paths.get(srcDir))
                        .filter { Files.isRegularFile(it) }
                        .forEach {
                            log.info("tar << $it")
                            appendTarEntry(to, it.toString())
                        }
                }
            }
        }
    } // end-of-companion
}
