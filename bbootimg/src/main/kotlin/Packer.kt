package cfig

import cfig.bootimg.BootImgInfo
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.apache.commons.exec.PumpStreamHandler
import org.junit.Assert
import org.junit.Assert.assertTrue
import org.slf4j.LoggerFactory
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

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
                    log.debug("update SIZE $item: " + Helper.toHexString((md.clone() as MessageDigest).digest()))
                }
            }
        }

        return md.digest()
    }

    private fun writePaddedFile(inBF: ByteBuffer, srcFile: String, padding: UInt) {
        Assert.assertTrue(padding < Int.MAX_VALUE.toUInt())
        writePaddedFile(inBF, srcFile, padding.toInt())
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

    private fun padFile(inBF: ByteBuffer, padding: UInt) {
        Assert.assertTrue(padding < Int.MAX_VALUE.toUInt())
        padFile(inBF, padding.toInt())
    }

    private fun padFile(inBF: ByteBuffer, padding: Int) {
        val pad = padding - (inBF.position() and padding - 1) and padding - 1
        inBF.put(ByteArray(pad))
    }

    private fun writeData(info2: BootImgInfo, outputFile: String) {
        log.info("Writing data ...")
        val param = ParamConfig()

        val bf = ByteBuffer.allocate(1024 * 1024 * 64)//assume total SIZE small than 64MB
        bf.order(ByteOrder.LITTLE_ENDIAN)

        writePaddedFile(bf, param.kernel, info2.pageSize)
        if (info2.ramdiskLength > 0U) {
            writePaddedFile(bf, param.ramdisk!!, info2.pageSize)
        }
        if (info2.secondBootloaderLength > 0U) {
            writePaddedFile(bf, param.second!!, info2.pageSize)
        }
        if (info2.recoveryDtboLength > 0U) {
            writePaddedFile(bf, param.dtbo!!, info2.pageSize)
        }
        if (info2.dtbLength > 0U) {
            writePaddedFile(bf, param.dtb!!, info2.pageSize)
        }
        //write
        FileOutputStream(outputFile + ".clear", true).use { fos ->
            fos.write(bf.array(), 0, bf.position())
        }
    }

    fun packRootfs(mkbootfs: String) {
        val param = ParamConfig()
        log.info("Packing rootfs ${UnifiedConfig.workDir}root ...")
        val outputStream = ByteArrayOutputStream()
        val exec = DefaultExecutor()
        exec.streamHandler = PumpStreamHandler(outputStream)
        val cmdline = "$mkbootfs ${UnifiedConfig.workDir}root"
        log.info(cmdline)
        exec.execute(CommandLine.parse(cmdline))
        Helper.gnuZipFile2(param.ramdisk!!, ByteArrayInputStream(outputStream.toByteArray()))
        log.info("${param.ramdisk} is ready")
    }

    private fun File.deleleIfExists() {
        if (this.exists()) {
            if (!this.isFile) {
                throw IllegalStateException("${this.canonicalPath} should be regular file")
            }
            log.info("Deleting ${this.path} ...")
            this.delete()
        }
    }

    fun pack(mkbootfsBin: String) {
        val param = ParamConfig()
        log.info("Loading config from ${param.cfg}")
        val cfg = ObjectMapper().readValue(File(param.cfg), UnifiedConfig::class.java)
        val info2 = cfg.toBootImgInfo()

        //clean
        File(cfg.info.output + ".google").deleleIfExists()
        File(cfg.info.output + ".clear").deleleIfExists()
        File(cfg.info.output + ".signed").deleleIfExists()
        File(cfg.info.output + ".signed2").deleleIfExists()
        File("${UnifiedConfig.workDir}ramdisk.img").deleleIfExists()

        if (info2.ramdiskLength > 0U) {
            if (File(param.ramdisk).exists() && !File(UnifiedConfig.workDir + "root").exists()) {
                //do nothing if we have ramdisk.img.gz but no /root
                log.warn("Use prebuilt ramdisk file: ${param.ramdisk}")
            } else {
                File(param.ramdisk).deleleIfExists()
                packRootfs(mkbootfsBin)
            }
        }

        val encodedHeader = info2.encode()
        //write
        FileOutputStream(cfg.info.output + ".clear", false).use { fos ->
            fos.write(encodedHeader)
            fos.write(ByteArray(info2.pageSize.toInt() - encodedHeader.size))
        }
        writeData(info2, cfg.info.output)

        val googleCmd = info2.toCommandLine().apply {
            addArgument(cfg.info.output + ".google")
        }
        DefaultExecutor().execute(googleCmd)

        val ourHash = hashFileAndSize(cfg.info.output + ".clear")
        val googleHash = hashFileAndSize(cfg.info.output + ".google")
        log.info("ours hash ${Helper.toHexString(ourHash)}, google's hash ${Helper.toHexString(googleHash)}")
        if (ourHash.contentEquals(googleHash)) {
            log.info("Hash verification passed: ${Helper.toHexString(ourHash)}")
        } else {
            log.error("Hash verification failed")
            throw UnknownError("Do not know why hash verification fails, maybe a bug")
        }
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
}
