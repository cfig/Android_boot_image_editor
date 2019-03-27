package cfig

import cfig.io.Struct
import com.google.common.math.BigIntegerMath
import org.apache.commons.codec.binary.Hex
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipParameters
import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.apache.commons.exec.ExecuteException
import org.apache.commons.exec.PumpStreamHandler
import org.junit.Assert
import org.junit.Assert.assertTrue
import org.slf4j.LoggerFactory
import java.io.*
import java.math.BigInteger
import java.math.RoundingMode
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher

class Helper {
    companion object {
        fun joinWithNulls(vararg source: ByteArray?): ByteArray {
            val baos = ByteArrayOutputStream()
            for (src in source) {
                src?.let {
                    if (src.isNotEmpty()) baos.write(src)
                }
            }
            return baos.toByteArray()
        }

        fun join(vararg source: ByteArray): ByteArray {
            val baos = ByteArrayOutputStream()
            for (src in source) {
                if (src.isNotEmpty()) baos.write(src)
            }
            return baos.toByteArray()
        }

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
        fun toCString(ba: ByteArray): String {
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

        fun extractFile(fileName: String, outImgName: String, offset: Long, length: Int) {
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

        fun round_to_multiple(size: Long, page: Int): Long {
            val remainder = size % page
            return if (remainder == 0L) {
                size
            } else {
                size + page - remainder
            }
        }

        /*
            read RSA private key
            assert exp == 65537
            num_bits = log2(modulus)

            @return: AvbRSAPublicKeyHeader formatted bytearray
                    https://android.googlesource.com/platform/external/avb/+/master/libavb/avb_crypto.h#158
            from avbtool::encode_rsa_key()
         */
        fun encodeRSAkey(key: ByteArray): ByteArray {
            val rsa = KeyUtil.parsePemPrivateKey(ByteArrayInputStream(key))
            Assert.assertEquals(65537.toBigInteger(), rsa.publicExponent)
            val numBits: Int = BigIntegerMath.log2(rsa.modulus, RoundingMode.CEILING)
            log.debug("modulus: " + rsa.modulus)
            log.debug("numBits: $numBits")
            val b = BigInteger.valueOf(2).pow(32)
            val n0inv = (b - rsa.modulus.modInverse(b)).toLong()
            log.debug("n0inv = $n0inv")
            val r = BigInteger.valueOf(2).pow(numBits)
            val rrModn = (r * r).mod(rsa.modulus)
            log.debug("BB: " + numBits / 8 + ", mod_len: " + rsa.modulus.toByteArray().size + ", rrmodn = " + rrModn.toByteArray().size)
            val unsignedModulo = rsa.modulus.toByteArray().sliceArray(1..numBits / 8) //remove sign byte
            log.debug("unsigned modulo: " + Hex.encodeHexString(unsignedModulo))
            val ret = Struct("!II${numBits / 8}b${numBits / 8}b").pack(
                    numBits,
                    n0inv,
                    unsignedModulo,
                    rrModn.toByteArray())
            log.debug("rrmodn: " + Hex.encodeHexString(rrModn.toByteArray()))
            log.debug("RSA: " + Hex.encodeHexString(ret))
            return ret
        }

        //inspired by
        //  https://stackoverflow.com/questions/40242391/how-can-i-sign-a-raw-message-without-first-hashing-it-in-bouncy-castle
        // "specifying Cipher.ENCRYPT mode or Cipher.DECRYPT mode doesn't make a difference;
        //      both simply perform modular exponentiation"
        fun rawSign(keyPath: String, data: ByteArray): ByteArray {
            val privk = KeyUtil.parsePk8PrivateKey(Files.readAllBytes(Paths.get(keyPath)))
            val cipher = Cipher.getInstance("RSA/ECB/NoPadding").apply {
                this.init(Cipher.ENCRYPT_MODE, privk)
                this.update(data)
            }
            return cipher.doFinal()
        }

        fun rawSignOpenSsl(keyPath: String, data: ByteArray): ByteArray {
            log.debug("raw input: " + Hex.encodeHexString(data))
            log.debug("Raw sign data size = ${data.size}, key = $keyPath")
            var ret = byteArrayOf()
            val exe = DefaultExecutor()
            val stdin = ByteArrayInputStream(data)
            val stdout = ByteArrayOutputStream()
            val stderr = ByteArrayOutputStream()
            exe.streamHandler = PumpStreamHandler(stdout, stderr, stdin)
            try {
                exe.execute(CommandLine.parse("openssl rsautl -sign -inkey $keyPath -raw"))
                ret = stdout.toByteArray()
                log.debug("Raw signature size = " + ret.size)
            } catch (e: ExecuteException) {
                log.error("Execute error")
            } finally {
                log.debug("OUT: " + Hex.encodeHexString(stdout.toByteArray()))
                log.debug("ERR: " + String(stderr.toByteArray()))
            }

            if (ret.isEmpty()) throw RuntimeException("raw sign failed")

            return ret
        }

        fun pyAlg2java(alg: String): String {
            return when (alg) {
                "sha1" -> "sha-1"
                "sha224" -> "sha-224"
                "sha256" -> "sha-256"
                "sha384" -> "sha-384"
                "sha512" -> "sha-512"
                else -> throw IllegalArgumentException("unknown algorithm: $alg")
            }
        }

        fun dumpToFile(dumpFile: String, data: ByteArray) {
            log.info("Dumping data to $dumpFile ...")
            FileOutputStream(dumpFile, false).use { fos ->
                fos.write(data)
            }
            log.info("Dumping data to $dumpFile done")
        }

        private val log = LoggerFactory.getLogger("Helper")
    }
}
