package cfig

import cfig.bootimg.BootImgInfo
import cfig.dtb_util.DTC
import cfig.kernel_util.KernelExtractor
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream

@OptIn(ExperimentalUnsignedTypes::class)
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

    private fun parseKernelInfo(kernelFile: String) {
        val ke = KernelExtractor()
        if (ke.envCheck()) {
            ke.run(kernelFile, File("."))
        }
    }

    fun extractBootImg(fileName: String, info2: BootImgInfo) {
        val param = ParamConfig()

        InfoTable.instance.addRule()
        if (info2.kernelLength > 0U) {
            Helper.extractFile(fileName,
                    param.kernel,
                    info2.kernelPosition.toLong(),
                    info2.kernelLength.toInt())
            log.info(" kernel  dumped  to: ${param.kernel}, size=${info2.kernelLength.toInt() / 1024.0 / 1024.0}MB")
            InfoTable.instance.addRow("kernel", param.kernel)
            parseKernelInfo(param.kernel)
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
            InfoTable.instance.addRule()
            InfoTable.instance.addRow("ramdisk", param.ramdisk!!.removeSuffix(".gz"))
            InfoTable.instance.addRow("\\-- extracted ramdisk rootfs", "${UnifiedConfig.workDir}root")
        } else {
            InfoTable.missingParts.add("ramdisk")
            log.info("no ramdisk found")
        }

        if (info2.secondBootloaderLength > 0U) {
            Helper.extractFile(fileName,
                    param.second!!,
                    info2.secondBootloaderPosition.toLong(),
                    info2.secondBootloaderLength.toInt())
            log.info("second bootloader dumped to ${param.second}")
            InfoTable.instance.addRule()
            InfoTable.instance.addRow("second bootloader", param.second)
        } else {
            InfoTable.missingParts.add("second bootloader")
            log.info("no second bootloader found")
        }

        if (info2.recoveryDtboLength > 0U) {
            Helper.extractFile(fileName,
                    param.dtbo!!,
                    info2.recoveryDtboPosition.toLong(),
                    info2.recoveryDtboLength.toInt())
            log.info("recovery dtbo dumped to ${param.dtbo}")
            InfoTable.instance.addRule()
            InfoTable.instance.addRow("recovery dtbo", param.dtbo)
        } else {
            InfoTable.missingParts.add("recovery dtbo")
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
            InfoTable.instance.addRule()
            InfoTable.instance.addRow("dtb", param.dtb)
            //extract DTB
            if (EnvironmentVerifier().hasDtc) {
                if (DTC().decompile(param.dtb!!, param.dtb + ".src")) {
                    InfoTable.instance.addRow("\\-- decompiled dts", param.dtb + ".src")
                }
            }
            //extract DTB
        } else {
            InfoTable.missingParts.add("dtb")
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
            try {
                DefaultExecutor().execute(CommandLine.parse(cmdline))
            } catch (e: Exception) {
                throw IllegalArgumentException("$fileName failed integrity check by \"$cmdline\"")
            }
        }
    }
}
