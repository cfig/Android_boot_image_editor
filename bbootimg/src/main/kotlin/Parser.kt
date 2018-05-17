package cfig

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.lang.IllegalStateException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.apache.commons.exec.PumpStreamHandler
import java.io.ByteArrayOutputStream
import java.util.regex.Pattern


class Parser {
    private val workDir = UnifiedConfig.workDir
    private fun readInt(iS: InputStream): Int {
        val bf = ByteBuffer.allocate(128)
        bf.order(ByteOrder.LITTLE_ENDIAN)
        val data4 = ByteArray(4)
        assertTrue(4 == iS.read(data4))
        bf.clear()
        bf.put(data4)
        bf.flip()
        return bf.int
    }

    private fun readUnsignedAsLong(iS: InputStream): Long {
        val bf = ByteBuffer.allocate(128)
        bf.order(ByteOrder.LITTLE_ENDIAN)
        val data4 = ByteArray(4)
        assertTrue(4 == iS.read(data4))
        bf.clear()
        bf.put(data4)
        bf.put(ByteArray(4)) //complete high bits with 0
        bf.flip()
        return bf.long
    }

    private fun readLong(iS: InputStream): Long {
        val bf = ByteBuffer.allocate(128)
        bf.order(ByteOrder.LITTLE_ENDIAN)
        val data4 = ByteArray(8)
        assertTrue(8 == iS.read(data4))
        bf.clear()
        bf.put(data4)
        bf.flip()
        return bf.long
    }

    private fun readBytes(iS: InputStream, len: Int): ByteArray {
        val data4 = ByteArray(len)
        assertTrue(len == iS.read(data4))
        return data4
    }

    private fun parseOsVersion(x: Int): String {
        val a = x shr 14
        val b = x - (a shl 14) shr 7
        val c = x and 0x7f

        return String.format("%d.%d.%d", a, b, c)
    }

    private fun parseOsPatchLevel(x: Int): String {
        var y = x shr 4
        val m = x and 0xf
        y += 2000

        return String.format("%d-%02d-%02d", y, m, 0)
    }

    private fun getHeaderSize(pageSize: Int): Int {
        val pad = (pageSize - (1648 and (pageSize - 1))) and (pageSize - 1)
        return pad + 1648
    }

    private fun getPaddingSize(position: Int, pageSize: Int): Int {
        return (pageSize - (position and pageSize - 1)) and (pageSize - 1)
    }

    private fun parseHeader(args: ImgArgs, info: ImgInfo) {
        FileInputStream(args.output).use { iS ->
            assertTrue(readBytes(iS, 8).contentEquals("ANDROID!".toByteArray()))
            info.kernelLength = readInt(iS)
            args.kernelOffset = readUnsignedAsLong(iS)
            info.ramdiskLength = readInt(iS)
            args.ramdiskOffset = readUnsignedAsLong(iS)
            info.secondBootloaderLength = readInt(iS)
            args.secondOffset = readUnsignedAsLong(iS)
            args.tagsOffset = readUnsignedAsLong(iS)
            args.pageSize = readInt(iS)
            args.headerVersion = readInt(iS)

            val osNPatch = readInt(iS)
            if (0 != osNPatch) { //treated as 'reserved' in this boot image
                args.osVersion = parseOsVersion(osNPatch shr 11)
                args.osPatchLevel = parseOsPatchLevel(osNPatch and 0x7ff)
            }

            args.board = Helper.byteArray2CString(readBytes(iS, 16))
            if (args.board.isBlank()) {
                args.board = ""
            }

            val cmd1 = Helper.byteArray2CString(readBytes(iS, 512))
            info.hash = readBytes(iS, 32) //hash
            val cmd2 = Helper.byteArray2CString(readBytes(iS, 1024))
            args.cmdline = cmd1 + cmd2

            info.recoveryDtboLength = readInt(iS)
            args.dtboOffset = readLong(iS)
            info.headerSize = readInt(iS)

            //calc subimg positions
            info.kernelPosition = getHeaderSize(args.pageSize)
            info.ramdiskPosition = info.kernelPosition + info.kernelLength + getPaddingSize(info.kernelLength, args.pageSize)
            info.secondBootloaderPosition = info.ramdiskPosition + info.ramdiskLength + getPaddingSize(info.ramdiskLength, args.pageSize)
            info.recoveryDtboPosition = info.secondBootloaderPosition + info.secondBootloaderLength + getPaddingSize(info.secondBootloaderLength, args.pageSize)

            //adjust args
            if (args.kernelOffset > Int.MAX_VALUE) {
                args.base = Int.MAX_VALUE + 1L
                args.kernelOffset -= args.base
                args.ramdiskOffset -= args.base
                args.secondOffset -= args.base
                args.tagsOffset -= args.base
                args.dtboOffset -= args.base
            }

            if (info.ramdiskLength == 0) args.ramdisk = null
            if (info.kernelLength == 0) throw IllegalStateException("boot image has no kernel")
            if (info.secondBootloaderLength == 0) args.second = null
            if (info.recoveryDtboLength == 0) args.dtbo = null
        }
    }//resource-closable

    private fun verifiedWithAVB(args: ImgArgs): Boolean {
        val expectedBf = "AVBf".toByteArray()
        FileInputStream(args.output).use { fis ->
            fis.skip(File(args.output).length() - 64)
            val bf = ByteArray(4)
            fis.read(bf)
            return bf.contentEquals(expectedBf)
        }
    }

    private fun verifyAVBIntegrity(args: ImgArgs, avbtool: String) {
        val cmdline = "$avbtool verify_image --image ${args.output}"
        log.info(cmdline)
        DefaultExecutor().execute(CommandLine.parse(cmdline))
    }

    private fun parseAVBInfo(args: ImgArgs, info: ImgInfo, avbtool: String) {
        val outputStream = ByteArrayOutputStream()
        val exec = DefaultExecutor()
        exec.streamHandler = PumpStreamHandler(outputStream)
        val cmdline = "$avbtool info_image --image ${args.output}"
        log.info(cmdline)
        exec.execute(CommandLine.parse(cmdline))
        val lines = outputStream.toString().split("\n")
        lines.forEach {
            val m = Pattern.compile("^Original image size:\\s+(\\d+)\\s*bytes").matcher(it)
            if (m.find()) {
                (info.signature as ImgInfo.AvbSignature).originalImageSize = Integer.parseInt(m.group(1))
            }

            val m2 = Pattern.compile("^Image size:\\s+(\\d+)\\s*bytes").matcher(it)
            if (m2.find()) {
                (info.signature as ImgInfo.AvbSignature).imageSize = Integer.parseInt(m2.group(1))
            }

            val m3 = Pattern.compile("^\\s*Partition Name:\\s+(\\S+)$").matcher(it)
            if (m3.find()) {
                (info.signature as ImgInfo.AvbSignature).partName = m3.group(1)

            }

            val m4 = Pattern.compile("^\\s*Salt:\\s+(\\S+)$").matcher(it)
            if (m4.find()) {
                (info.signature as ImgInfo.AvbSignature).salt = m4.group(1)

            }

            log.debug("[" + it + "]")
        }
        assertNotNull((info.signature as ImgInfo.AvbSignature).imageSize)
        assertNotNull((info.signature as ImgInfo.AvbSignature).originalImageSize)
        assertTrue(!(info.signature as ImgInfo.AvbSignature).partName.isNullOrBlank())
        assertTrue(!(info.signature as ImgInfo.AvbSignature).salt.isNullOrBlank())
    }

    private fun unpackRamdisk(imgArgs: ImgArgs) {
        val exe = DefaultExecutor()
        exe.workingDirectory = File(workDir + "root")
        if (exe.workingDirectory.exists()) exe.workingDirectory.deleteRecursively()
        exe.workingDirectory.mkdirs()
        val ramdiskFile = File(imgArgs.ramdisk!!.removeSuffix(".gz"))
        exe.execute(CommandLine.parse("cpio -i -m -F " + ramdiskFile.canonicalPath))
        log.info("extract ramdisk done: $ramdiskFile -> ${exe.workingDirectory.path}")
    }

    fun parseAndExtract(fileName: String?, avbtool: String) {
        val imgArgs = ImgArgs(output = fileName ?: "boot.img")
        val imgInfo = ImgInfo()
        if (File(workDir).exists()) File(workDir).deleteRecursively()
        File(workDir).mkdirs()
        if (!fileName.isNullOrBlank()) {
            imgArgs.output = fileName!!
        }

        //parse header
        parseHeader(imgArgs, imgInfo)

        //parse signature
        if (verifiedWithAVB(imgArgs)) {
            imgArgs.verifyType = ImgArgs.VerifyType.AVB
            imgInfo.signature = ImgInfo.AvbSignature()
            verifyAVBIntegrity(imgArgs, avbtool)
            parseAVBInfo(imgArgs, imgInfo, avbtool)
        } else {
            imgArgs.verifyType = ImgArgs.VerifyType.VERIFY
            imgInfo.signature = ImgInfo.VeritySignature()
        }

        log.info(imgArgs.toString())
        log.info(imgInfo.toString())

        Helper.extractImageData(imgArgs.output, imgArgs.kernel, imgInfo.kernelPosition.toLong(), imgInfo.kernelLength)
        log.info("kernel dumped to ${imgArgs.kernel}")
        imgArgs.ramdisk?.let { ramdisk ->
            log.info("ramdisk dumped to ${imgArgs.ramdisk}")
            Helper.extractImageData(imgArgs.output, ramdisk, imgInfo.ramdiskPosition.toLong(), imgInfo.ramdiskLength)
            Helper.unGnuzipFile(ramdisk, workDir + "ramdisk.img")
            unpackRamdisk(imgArgs)
        }
        imgArgs.second?.let { second ->
            Helper.extractImageData(imgArgs.output, second, imgInfo.secondBootloaderPosition.toLong(), imgInfo.secondBootloaderLength)
            log.info("second bootloader dumped to ${imgArgs.second}")
        }
        imgArgs.dtbo?.let { dtbo ->
            Helper.extractImageData(imgArgs.output, dtbo, imgInfo.recoveryDtboPosition.toLong(), imgInfo.recoveryDtboLength)
            log.info("dtbo dumped to ${imgArgs.dtbo}")
        }
        val cfg = UnifiedConfig.fromArgs(imgArgs, imgInfo)
        log.debug(ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(cfg))
        ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(File(imgArgs.cfg), cfg)
        log.info("image info written to ${imgArgs.cfg}")
    }

    companion object {
        private val log = LoggerFactory.getLogger("Parser")!!
    }
}
