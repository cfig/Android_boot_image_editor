package cfig

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.slf4j.LoggerFactory
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.regex.Pattern
import org.junit.Assert.*

class Packer {
    private val log = LoggerFactory.getLogger("Packer")
    private val workDir = UnifiedConfig.workDir

    @Throws(CloneNotSupportedException::class)
    private fun hashFileAndSize(vararg inFiles: String?): ByteArray {
        val md = MessageDigest.getInstance("SHA1")
        for (item in inFiles) {
            if (null == item) {
                md.update(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                        .putInt(0)
                        .array())
                log.debug("update null $item: " + Helper.toHexString((md.clone() as MessageDigest).digest()))
            } else {
                val currentFile = File(item)
                FileInputStream(currentFile).use { iS ->
                    var byteRead: Int
                    var dataRead = ByteArray(1024)
                    while (true) {
                        byteRead = iS.read(dataRead)
                        if (-1 == byteRead) {
                            break
                        }
                        md.update(dataRead, 0, byteRead)
                    }
                    log.debug("update file $item: " + Helper.toHexString((md.clone() as MessageDigest).digest()))
                    md.update(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                            .putInt(currentFile.length().toInt())
                            .array())
                    log.debug("update size $item: " + Helper.toHexString((md.clone() as MessageDigest).digest()))
                }
            }
        }

        return md.digest()
    }

    private fun writePaddedFile(inBF: ByteBuffer, srcFile: String, padding: Int) {
        FileInputStream(srcFile).use { iS ->
            var byteRead: Int
            val dataRead = ByteArray(128)
            while (true) {
                byteRead = iS.read(dataRead)
                if (-1 == byteRead) {
                    break
                }
                inBF.put(dataRead, 0, byteRead)
            }
            padFile(inBF, padding)
        }
    }

    private fun padFile(inBF: ByteBuffer, padding: Int) {
        val pad = padding - (inBF.position() and padding - 1) and padding - 1
        inBF.put(ByteArray(pad))
    }

    private fun writeData(inArgs: ImgArgs) {
        log.info("Writing data ...")
        val bf = ByteBuffer.allocate(1024 * 1024 * 64)//assume total size small than 64MB
        bf.order(ByteOrder.LITTLE_ENDIAN)

        writePaddedFile(bf, inArgs.kernel, inArgs.pageSize)
        inArgs.ramdisk?.let { ramdisk ->
            writePaddedFile(bf, ramdisk, inArgs.pageSize)
        }
        inArgs.second?.let { second ->
            writePaddedFile(bf, second, inArgs.pageSize)
        }
        inArgs.dtbo?.let { dtbo ->
            writePaddedFile(bf, dtbo, inArgs.pageSize)
        }
        //write
        FileOutputStream(inArgs.output + ".clear", true).use { fos ->
            fos.write(bf.array(), 0, bf.position())
        }
    }

    @Throws(IllegalArgumentException::class)
    private fun packOsVersion(x: String?): Int {
        if (x.isNullOrBlank()) return 0
        val pattern = Pattern.compile("^(\\d{1,3})(?:\\.(\\d{1,3})(?:\\.(\\d{1,3}))?)?")
        val m = pattern.matcher(x)
        if (m.find()) {
            val a = Integer.decode(m.group(1))
            var b = 0
            var c = 0
            if (m.groupCount() >= 2) {
                b = Integer.decode(m.group(2))
            }
            if (m.groupCount() == 3) {
                c = Integer.decode(m.group(3))
            }
            assertTrue(a < 128)
            assertTrue(b < 128)
            assertTrue(c < 128)
            return (a shl 14) or (b shl 7) or c
        } else {
            throw IllegalArgumentException("invalid os_version")
        }
    }

    private fun parseOsPatchLevel(x: String?): Int {
        if (x.isNullOrBlank()) return 0
        val ret: Int
        val pattern = Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})")
        val matcher = pattern.matcher(x)
        if (matcher.find()) {
            val y = Integer.parseInt(matcher.group(1), 10) - 2000
            val m = Integer.parseInt(matcher.group(2), 10)
            // 7 bits allocated for the year, 4 bits for the month
            assertTrue(y in 0..127)
            assertTrue(m in 1..12)
            ret = (y shl 4) or m
        } else {
            throw IllegalArgumentException("invalid os_patch_level")
        }

        return ret
    }

    private fun writeHeader(inArgs: ImgArgs): ByteArray {
        log.info("Writing header ...")
        val bf = ByteBuffer.allocate(1024 * 32)
        bf.order(ByteOrder.LITTLE_ENDIAN)

        //header start
        bf.put("ANDROID!".toByteArray())
        bf.putInt(File(inArgs.kernel).length().toInt())
        bf.putInt((inArgs.base + inArgs.kernelOffset).toInt())

        if (null == inArgs.ramdisk) {
            bf.putInt(0)
        } else {
            bf.putInt(File(inArgs.ramdisk).length().toInt())
        }

        bf.putInt((inArgs.base + inArgs.ramdiskOffset).toInt())

        if (null == inArgs.second) {
            bf.putInt(0)
        } else {
            bf.putInt(File(inArgs.second).length().toInt())
        }

        bf.putInt((inArgs.base + inArgs.secondOffset).toInt())
        bf.putInt((inArgs.base + inArgs.tagsOffset).toInt())
        bf.putInt(inArgs.pageSize)
        bf.putInt(inArgs.headerVersion)
        bf.putInt((packOsVersion(inArgs.osVersion) shl 11) or parseOsPatchLevel(inArgs.osPatchLevel))

        if (inArgs.board.isBlank()) {
            bf.put(ByteArray(16))
        } else {
            bf.put(inArgs.board.toByteArray())
            bf.put(ByteArray(16 - inArgs.board.length))
        }

        bf.put(inArgs.cmdline.substring(0, minOf(512, inArgs.cmdline.length)).toByteArray())
        bf.put(ByteArray(512 - minOf(512, inArgs.cmdline.length)))

        //hash
        val imageId = if (inArgs.headerVersion > 0) {
            hashFileAndSize(inArgs.kernel, inArgs.ramdisk, inArgs.second, inArgs.dtbo)
        } else {
            hashFileAndSize(inArgs.kernel, inArgs.ramdisk, inArgs.second)
        }
        bf.put(imageId)
        bf.put(ByteArray(32 - imageId.size))

        if (inArgs.cmdline.length > 512) {
            bf.put(inArgs.cmdline.substring(512).toByteArray())
            bf.put(ByteArray(1024 + 512 - inArgs.cmdline.length))
        } else {
            bf.put(ByteArray(1024))
        }

        if (inArgs.headerVersion > 0) {
            if (inArgs.dtbo == null) {
                bf.putInt(0)
            } else {
                bf.putInt(File(inArgs.dtbo).length().toInt())
            }
            bf.putLong(inArgs.dtboOffset)
            bf.putInt(1648)
        }

        //padding
        padFile(bf, inArgs.pageSize)

        //write
        FileOutputStream(inArgs.output + ".clear", false).use { fos ->
            fos.write(bf.array(), 0, bf.position())
        }

        return imageId
    }

    fun pack(mkbootimgBin: String) {
        log.info("Loading config from ${workDir}bootimg.json")
        val cfg = ObjectMapper().readValue(File(workDir + "bootimg.json"), UnifiedConfig::class.java)
        val readBack = cfg.toArgs()
        val args = readBack[0] as ImgArgs
        val info = readBack[1] as ImgInfo
        args.mkbootimg = mkbootimgBin
        log.debug(args.toString())
        log.debug(info.toString())

        //clean
        if (File(args.output + ".google").exists()) File(args.output + ".google").delete()
        if (File(args.output + ".clear").exists()) File(args.output + ".clear").delete()
        if (File(args.output + ".signed").exists()) File(args.output + ".signed").delete()

        writeHeader(args)
        writeData(args)

        DefaultExecutor().execute(args.toCommandLine())
        val ourHash = hashFileAndSize(args.output + ".clear")
        val googleHash = hashFileAndSize(args.output + ".google")
        log.info("ours hash ${Helper.toHexString(ourHash)}, google's hash ${Helper.toHexString(googleHash)}")
        if (ourHash.contentEquals(googleHash)) {
            log.info("Hash verification passed: ${Helper.toHexString(ourHash)}")
        } else {
            log.error("Hash verification failed")
            throw UnknownError("Do not know why hash verification fails, maybe a bug")
        }
    }

    fun sign(avbtool: String, bootSigner: String) {
        log.info("Loading config from ${workDir}bootimg.json")
        val cfg = ObjectMapper().readValue(File(workDir + "bootimg.json"), UnifiedConfig::class.java)
        val readBack = cfg.toArgs()
        val args = readBack[0] as ImgArgs
        val info = readBack[1] as ImgInfo

        when (args.verifyType) {
            ImgArgs.VerifyType.VERIFY -> {
                log.info("Signing with verified-boot 1.0 style")
                val sig = ObjectMapper().readValue(
                        mapToJson(info.signature as LinkedHashMap<*, *>), ImgInfo.VeritySignature::class.java)
                DefaultExecutor().execute(CommandLine.parse("java -jar $bootSigner " +
                        "${sig.path} ${args.output}.clear ${sig.verity_pk8} ${sig.verity_pem} ${args.output}.signed"))

            }
            ImgArgs.VerifyType.AVB -> {
                log.info("Adding hash_footer with verified-boot 2.0 style")
                val sig = ObjectMapper().readValue(
                        mapToJson(info.signature as LinkedHashMap<*, *>), ImgInfo.AvbSignature::class.java)
                File(args.output + ".clear").copyTo(File(args.output + ".signed"))
                DefaultExecutor().execute(CommandLine.parse(
                                "$avbtool add_hash_footer " +
                                        "--image ${args.output}.signed " +
                                        "--partition_size ${sig.imageSize} " +
                                        "--partition_name ${sig.partName}"))
                verifyAVBIntegrity(args, info, avbtool)
            }
        }
    }

    private fun mapToJson(m: LinkedHashMap<*, *>): String {
        val sb = StringBuilder()
        m.forEach { k, v ->
            if (sb.isNotEmpty()) sb.append(", ")
            sb.append("\"$k\": \"$v\"")
        }
        return "{ $sb }"
    }

    private fun runCmdList(inCmd: List<String>, inWorkdir: String? = null) {
        log.info("CMD:$inCmd")
        val pb = ProcessBuilder(inCmd)
                .directory(File(inWorkdir ?: "."))
                .redirectErrorStream(true)
        val p: Process = pb.start()
        val br = BufferedReader(InputStreamReader(p.inputStream))
        while (br.ready()) {
            log.info(br.readLine())
        }
        p.waitFor()
        assertTrue(0 == p.exitValue())
    }

    private fun verifyAVBIntegrity(args: ImgArgs, info: ImgInfo, avbtool: String) {
        val tgt = args.output + ".signed"
        log.info("Verifying AVB: $tgt")
        DefaultExecutor().execute(CommandLine.parse("$avbtool verify_image --image $tgt"))
        log.info("Verifying image passed: $tgt")
    }
}
