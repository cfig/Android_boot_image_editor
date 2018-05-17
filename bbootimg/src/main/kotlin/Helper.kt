package cfig

import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipParameters
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import org.junit.Assert.*
import java.io.*

class Helper {
    companion object {
        fun toHexString(inData: ByteArray): String {
            val sb = StringBuilder()
            for (i in inData.indices) {
                sb.append(Integer.toString((inData[i].toInt().and(0xff)) + 0x100, 16).substring(1))
            }
            return sb.toString()
        }

        fun fromHexString(s: String): ByteArray {
            val len = s.length
            val data = ByteArray(len / 2)
            var i = 0
            while (i < len) {
                data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
                i += 2
            }
            return data
        }

        //similar to this.toString(StandardCharsets.UTF_8).replace("${Character.MIN_VALUE}", "")
        fun byteArray2CString(ba: ByteArray): String {
            val str = ba.toString(StandardCharsets.UTF_8)
            val nullPos = str.indexOf(Character.MIN_VALUE)
            return if (nullPos >= 0) {
                str.substring(0, nullPos)
            } else {
                str
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
                            gos.write(buffer, 0, bytesRead);
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
        fun gnuZipFile(compressedFile: String, fis: InputStream) {
            val buffer = ByteArray(1024)
            FileOutputStream(compressedFile).use {fos ->
                GZIPOutputStream(fos).use {gos ->
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
            FileOutputStream(compressedFile).use {fos ->
                GzipCompressorOutputStream(fos, p).use {gos ->
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

        fun extractImageData(fileName: String, outImgName: String, offset: Long, length: Int) {
            if (0 == length) {
                return
            }
            RandomAccessFile(fileName, "r").use { inRaf ->
                RandomAccessFile(outImgName, "rw").use { outRaf ->
                    inRaf.seek(offset)
                    val data = ByteArray(length)
                    assertTrue(length == inRaf.read(data))
                    outRaf.write(data)
                }
            }
        }

        private val log = LoggerFactory.getLogger("Helper")
    }
}
