package cfig

import cfig.bootimg.BootImgInfo
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.junit.Assert.assertTrue
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Parser {
    private fun verifiedWithAVB(fileName: String): Boolean {
        val expectedBf = "AVBf".toByteArray()
        FileInputStream(fileName).use { fis ->
            fis.skip(File(fileName).length() - 64)
            val bf = ByteArray(4)
            fis.read(bf)
            return bf.contentEquals(expectedBf)
        }
    }

    private fun unpackRamdisk(workDir: String, ramdiskGz: String) {
        val exe = DefaultExecutor()
        exe.workingDirectory = File(workDir + "root")
        if (exe.workingDirectory.exists()) exe.workingDirectory.deleteRecursively()
        exe.workingDirectory.mkdirs()
        val ramdiskFile = File(ramdiskGz.removeSuffix(".gz"))
        exe.execute(CommandLine.parse("cpio -i -m -F " + ramdiskFile.canonicalPath))
        log.info(" ramdisk extracted : $ramdiskFile -> ${exe.workingDirectory.path}")
    }

    fun parseBootImgHeader(fileName: String, avbtool: String): BootImgInfo {
        val info2 = BootImgInfo(FileInputStream(fileName))
        val param = ParamConfig()
        if (verifiedWithAVB(fileName)) {
            info2.signatureType = BootImgInfo.VerifyType.AVB
            verifyAVBIntegrity(fileName, avbtool)
        } else {
            info2.signatureType = BootImgInfo.VerifyType.VERIFY
        }
        info2.imageSize = File(fileName).length()

        val cfg = UnifiedConfig.fromBootImgInfo(info2).apply {
            info.output = File(fileName).name
        }

        ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(File(param.cfg), cfg)
        log.info("image info written to ${param.cfg}")

        return info2
    }

    fun extractBootImg(fileName: String, info2: BootImgInfo) {
        val param = ParamConfig()
        if (info2.kernelLength > 0U) {
            Helper.extractFile(fileName,
                    param.kernel,
                    info2.kernelPosition.toLong(),
                    info2.kernelLength.toInt())
            log.info(" kernel  dumped  to: ${param.kernel}, size=${info2.kernelLength.toInt() / 1024.0 / 1024.0}MB")
        } else {
            throw RuntimeException("bad boot image: no kernel found")
        }

        if (info2.ramdiskLength > 0U) {
            Helper.extractFile(fileName,
                    param.ramdisk!!,
                    info2.ramdiskPosition.toLong(),
                    info2.ramdiskLength.toInt())
            log.info("ramdisk  dumped  to: ${param.ramdisk}")
            Helper.unGnuzipFile(param.ramdisk!!, param.ramdisk!!.removeSuffix(".gz"))
            unpackRamdisk(UnifiedConfig.workDir, param.ramdisk!!.removeSuffix(".gz"))
        } else {
            log.info("no ramdisk found")
        }

        if (info2.secondBootloaderLength > 0U) {
            Helper.extractFile(fileName,
                    param.second!!,
                    info2.secondBootloaderPosition.toLong(),
                    info2.secondBootloaderLength.toInt())
            log.info("second bootloader dumped to ${param.second}")
        } else {
            log.info("no second bootloader found")
        }

        if (info2.recoveryDtboLength > 0U) {
            Helper.extractFile(fileName,
                    param.dtbo!!,
                    info2.recoveryDtboPosition.toLong(),
                    info2.recoveryDtboLength.toInt())
            log.info("dtbo dumped to ${param.dtbo}")
        } else {
            if (info2.headerVersion > 0U) {
                log.info("no recovery dtbo found")
            } else {
                log.debug("no recovery dtbo for header v0")
            }
        }

        if (info2.dtbLength > 0U) {
            Helper.extractFile(fileName,
                    param.dtb!!,
                    info2.dtbPosition.toLong(),
                    info2.dtbLength.toInt())
            log.info("dtb dumped to ${param.dtb}")
        } else {
            if (info2.headerVersion > 1U) {
                log.info("no dtb found")
            } else {
                log.debug("no dtb for header v0")
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger("Parser")!!

        fun verifyAVBIntegrity(fileName: String, avbtool: String) {
            val cmdline = "$avbtool verify_image --image $fileName"
            log.info(cmdline)
            DefaultExecutor().execute(CommandLine.parse(cmdline))
        }

        fun readShort(iS: InputStream): Short {
            val bf = ByteBuffer.allocate(128)
            bf.order(ByteOrder.LITTLE_ENDIAN)
            val data2 = ByteArray(2)
            assertTrue(2 == iS.read(data2))
            bf.clear()
            bf.put(data2)
            bf.flip()
            return bf.short
        }

        fun readInt(iS: InputStream): Int {
            val bf = ByteBuffer.allocate(128)
            bf.order(ByteOrder.LITTLE_ENDIAN)
            val data4 = ByteArray(4)
            assertTrue(4 == iS.read(data4))
            bf.clear()
            bf.put(data4)
            bf.flip()
            return bf.int
        }

        fun readUnsignedAsLong(iS: InputStream): Long {
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

        fun readLong(iS: InputStream): Long {
            val bf = ByteBuffer.allocate(128)
            bf.order(ByteOrder.LITTLE_ENDIAN)
            val data4 = ByteArray(8)
            assertTrue(8 == iS.read(data4))
            bf.clear()
            bf.put(data4)
            bf.flip()
            return bf.long
        }

        fun readBytes(iS: InputStream, len: Int): ByteArray {
            val data4 = ByteArray(len)
            assertTrue(len == iS.read(data4))
            return data4
        }
    }
}
