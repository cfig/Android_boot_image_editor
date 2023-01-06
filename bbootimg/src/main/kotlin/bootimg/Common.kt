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

package cfig.bootimg

import cc.cfig.io.Struct
import cfig.utils.EnvironmentVerifier
import cfig.bootimg.cpio.AndroidCpio
import cfig.utils.DTC
import cfig.helper.Helper
import cfig.helper.ZipHelper
import cfig.utils.KernelExtractor
import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.apache.commons.exec.PumpStreamHandler
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.io.FileInputStream
import java.lang.NumberFormatException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.*
import java.util.regex.Pattern

class Common {
    data class VeritySignature(
        var type: String = "dm-verity",
        var path: String = "/boot",
        var verity_pk8: String = "",
        var verity_pem: String = "",
        var jarPath: String = ""
    )

    companion object {
        private val log = LoggerFactory.getLogger(Common::class.java)
        private const val MAX_ANDROID_VER = 11

        @Throws(IllegalArgumentException::class)
        fun packOsVersion(x: String?): Int {
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
                check(a < 128)
                check(b < 128)
                check(c < 128)
                return (a shl 14) or (b shl 7) or c
            } else {
                throw IllegalArgumentException("invalid os_version")
            }
        }

        fun parseOsVersion(x: Int): String {
            val a = x shr 14
            val b = x - (a shl 14) shr 7
            val c = x and 0x7f
            return String.format(Locale.getDefault(), "%d.%d.%d", a, b, c)
        }

        fun packOsPatchLevel(x: String?): Int {
            if (x.isNullOrBlank()) return 0
            val ret: Int
            val pattern = Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})")
            val matcher = pattern.matcher(x)
            if (matcher.find()) {
                val y = Integer.parseInt(matcher.group(1), 10) - 2000
                val m = Integer.parseInt(matcher.group(2), 10)
                // 7 bits allocated for the year, 4 bits for the month
                check(y in 0..127)
                check(m in 1..12)
                ret = (y shl 4) or m
            } else {
                throw IllegalArgumentException("invalid os_patch_level")
            }

            return ret
        }

        fun parseOsPatchLevel(x: Int): String {
            var y = x shr 4
            val m = x and 0xf
            y += 2000
            return String.format("%d-%02d-%02d", y, m, 0)
        }

        fun parseKernelInfo(kernelFile: String): List<String> {
            KernelExtractor().let { ke ->
                if (ke.envCheck()) {
                    return ke.run(kernelFile, File("."))
                }
            }
            return listOf()
        }

        fun dumpKernel(s: Helper.Slice) {
            Helper.extractFile(s.srcFile, s.dumpFile, s.offset.toLong(), s.length)
            parseKernelInfo(s.dumpFile)
        }

        fun dumpRamdisk(s: Helper.Slice, root: String, unpackCpio: Boolean = true): String {
            var ret = "gz"
            Helper.extractFile(s.srcFile, s.dumpFile, s.offset.toLong(), s.length)
            when {
                ZipHelper.isGZ(s.dumpFile) -> {
                    Files.move(
                        Paths.get(s.dumpFile), Paths.get(s.dumpFile + ".gz"),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                    )
                    ZipHelper.zcat(s.dumpFile + ".gz", s.dumpFile)
                }
                ZipHelper.isXz(s.dumpFile) -> {
                    log.info("ramdisk is compressed xz")
                    Files.move(
                        Paths.get(s.dumpFile), Paths.get(s.dumpFile + ".xz"),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                    )
                    ZipHelper.xzcat(s.dumpFile + ".xz", s.dumpFile)
                    ret = "xz"
                }
                ZipHelper.isLzma(s.dumpFile) -> {
                    log.info("ramdisk is compressed lzma")
                    Files.move(
                            Paths.get(s.dumpFile), Paths.get(s.dumpFile + ".lzma"),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING
                    )
                    ZipHelper.lzcat(s.dumpFile + ".lzma", s.dumpFile)
                    ret = "lzma"
                }
                ZipHelper.isLz4(s.dumpFile) -> {
                    log.info("ramdisk is compressed lz4")
                    Files.move(
                        Paths.get(s.dumpFile), Paths.get(s.dumpFile + ".lz4"),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                    )
                    ZipHelper.lz4cat(s.dumpFile + ".lz4", s.dumpFile)
                    ret = "lz4"
                }
                ZipHelper.isAndroidCpio(s.dumpFile) -> {
                    log.info("ramdisk is uncompressed cpio")
                    Files.copy(
                        Paths.get(s.dumpFile), Paths.get(s.dumpFile + ".cpio"),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                    )
                    ret = "cpio"
                }
                else -> {
                    throw IllegalArgumentException("ramdisk is in unknown format")
                }
            }
            if (unpackCpio) {
                unpackRamdisk(s.dumpFile, root)
            }
            return ret
        }

        fun dumpDtb(s: Helper.Slice) {
            Helper.extractFile(s.srcFile, s.dumpFile, s.offset.toLong(), s.length)
            //extract DTB
            if (EnvironmentVerifier().hasDtc) {
                DTC().decompile(s.dumpFile, s.dumpFile + "." + Helper.prop("config.dts_suffix"))
            }
        }

        fun getPaddingSize(position: UInt, pageSize: UInt): UInt {
            return (pageSize - (position and pageSize - 1U)) and (pageSize - 1U)
        }

        fun getPaddingSize(position: Int, pageSize: Int): Int {
            return (pageSize - (position and pageSize - 1)) and (pageSize - 1)
        }

        @Throws(CloneNotSupportedException::class)
        fun hashFileAndSize(vararg inFiles: String?): ByteArray {
            val md = MessageDigest.getInstance("SHA1")
            for (item in inFiles) {
                if (null == item) {
                    md.update(
                        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                            .putInt(0)
                            .array()
                    )
                    log.debug("update null $item: " + Helper.toHexString((md.clone() as MessageDigest).digest()))
                } else {
                    val currentFile = File(item)
                    FileInputStream(currentFile).use { iS ->
                        var byteRead: Int
                        val dataRead = ByteArray(1024)
                        while (true) {
                            byteRead = iS.read(dataRead)
                            if (-1 == byteRead) {
                                break
                            }
                            md.update(dataRead, 0, byteRead)
                        }
                        log.debug("update file $item: " + Helper.toHexString((md.clone() as MessageDigest).digest()))
                        md.update(
                            ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                                .putInt(currentFile.length().toInt())
                                .array()
                        )
                        log.debug("update SIZE $item: " + Helper.toHexString((md.clone() as MessageDigest).digest()))
                    }
                }
            }

            return md.digest()
        }

        //using mkbootfs
        fun packRootfs(rootDir: String, ramdiskGz: String, osMajor: Int = 10) {
            val mkbootfs = String.format(Locale.getDefault(), Helper.prop("mkbootfsBin"), osMajor)
            log.info("Packing rootfs $rootDir ...")
            val outputStream = ByteArrayOutputStream()
            DefaultExecutor().let { exec ->
                exec.streamHandler = PumpStreamHandler(outputStream)
                val cmdline = "$mkbootfs $rootDir"
                log.info("CMD: $cmdline -> PIPE -> $ramdiskGz")
                exec.execute(CommandLine.parse(cmdline))
            }
            when {
                ramdiskGz.endsWith(".gz") -> {
                    ZipHelper.minigzip(ramdiskGz, ByteArrayInputStream(outputStream.toByteArray()))
                }
                ramdiskGz.endsWith(".lz4") -> {
                    ZipHelper.lz4(ramdiskGz, ByteArrayInputStream(outputStream.toByteArray()))
                }
                else -> {
                    throw IllegalArgumentException("$ramdiskGz is not supported")
                }
            }
            log.info("$ramdiskGz is ready")
        }

        //using preset fs_config
        fun packRootfs(rootDir: String, ramdiskGz: String) {
            val root = File(rootDir).path
            log.info("Packing rootfs $root ...")
            when {
                ramdiskGz.endsWith(".gz") -> {
                    val f = ramdiskGz.removeSuffix(".gz")
                    AndroidCpio().pack(root, f, "${f}_filelist.txt")
                    FileInputStream(f).use { ZipHelper.minigzip(ramdiskGz, it) }
                }
                ramdiskGz.endsWith(".lz4") -> {
                    val f = ramdiskGz.removeSuffix(".lz4")
                    AndroidCpio().pack(root, f, "${f}_filelist.txt")
                    FileInputStream(f).use { ZipHelper.lz4(ramdiskGz, it) }
                }
                ramdiskGz.endsWith(".lzma") -> {
                    val f = ramdiskGz.removeSuffix(".lzma")
                    AndroidCpio().pack(root, f, "${f}_filelist.txt")
                    FileInputStream(f).use { ZipHelper.lzma(ramdiskGz, it) }
                }
                ramdiskGz.endsWith(".xz") -> {
                    val f = ramdiskGz.removeSuffix(".xz")
                    AndroidCpio().pack(root, f, "${f}_filelist.txt")
                    FileInputStream(f).use { ZipHelper.xz(ramdiskGz, it) }
                }
                ramdiskGz.endsWith(".cpio") -> {
                    val f = ramdiskGz.removeSuffix(".cpio")
                    AndroidCpio().pack(root, f, "${f}_filelist.txt")
                    File(f).copyTo(File(ramdiskGz), true)
                }
                else -> {
                    throw IllegalArgumentException("$ramdiskGz is not supported")
                }
            }
            log.info("$ramdiskGz is ready")
        }

        fun padFile(inBF: ByteBuffer, padding: Int) {
            val pad = padding - (inBF.position() and padding - 1) and padding - 1
            inBF.put(ByteArray(pad))
        }

        fun File.deleleIfExists() {
            if (this.exists()) {
                if (!this.isFile) {
                    throw IllegalStateException("${this.canonicalPath} should be regular file")
                }
                log.info("Deleting ${this.path} ...")
                this.delete()
            }
        }

        fun writePaddedFile(inBF: ByteBuffer, srcFile: String, padding: UInt) {
            log.info("adding $srcFile into buffer ...")
            check(padding < Int.MAX_VALUE.toUInt())
            writePaddedFile(inBF, srcFile, padding.toInt())
        }

        fun writePaddedFile(inBF: ByteBuffer, srcFile: String, padding: Int) {
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

        fun writePaddedFiles(inBF: ByteBuffer, srcFiles: List<String>, padding: Int) {
            srcFiles.forEach { srcFile ->
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
                }
            }
            padFile(inBF, padding)
        }

        private fun unpackRamdisk(ramdisk: String, root: String) {
            val rootFile = File(root).apply {
                if (exists()) {
                    log.info("Cleaning [$root] before ramdisk unpacking")
                    deleteRecursively()
                }
                mkdirs()
            }

            AndroidCpio.decompressCPIO(
                File(ramdisk).canonicalPath,
                rootFile.canonicalPath,
                File(ramdisk).canonicalPath + "_filelist.txt"
            )
            log.info(" ramdisk extracted : $ramdisk -> $rootFile")
        }

        fun probeHeaderVersion(fileName: String): Int {
            return FileInputStream(fileName).let { fis ->
                fis.skip(40)
                Struct.IntShip().get(fis, ByteOrder.LITTLE_ENDIAN)
            }
        }

        fun parseOsMajor(osVersion: String): Int {
            return try {
                log.info("OS Major: " + osVersion.split(".")[0])
                val ret = Integer.parseInt(osVersion.split(".")[0])
                when {
                    ret > MAX_ANDROID_VER -> {
                        log.warn("Os Major exceeds current max $MAX_ANDROID_VER")
                        MAX_ANDROID_VER
                    }
                    ret < 10 -> {
                        10
                    }
                    else -> {
                        ret
                    }
                }
            } catch (e: NumberFormatException) {
                log.warn("can not parse osVersion from $osVersion")
                10
            }
        }
    }
}
