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

package cfig.bootimg.v3

import avb.AVBInfo
import avb.alg.Algorithms
import avb.blob.AuxBlob
import cfig.Avb
import cfig.bootimg.Common
import cfig.utils.EnvironmentVerifier
import cfig.bootimg.Common.Companion.deleleIfExists
import cfig.bootimg.Common.Companion.getPaddingSize
import cfig.bootimg.Signer
import cfig.helper.Helper
import cfig.helper.Dumpling
import cfig.helper.ZipHelper
import cfig.packable.VBMetaParser
import com.fasterxml.jackson.databind.ObjectMapper
import de.vandermeer.asciitable.AsciiTable
import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import cfig.bootimg.Common as C

data class BootV3(
    var info: MiscInfo = MiscInfo(),
    var kernel: CommArgs = CommArgs(),
    val ramdisk: RamdiskArgs = RamdiskArgs(),
    var bootSignature: CommArgs = CommArgs(),
) {
    companion object {
        private val log = LoggerFactory.getLogger(BootV3::class.java)
        private val errLog = LoggerFactory.getLogger("uiderrors")
        private val mapper = ObjectMapper()
        private val workDir = Helper.prop("workDir")!!

        fun parse(fileName: String): BootV3 {
            val ret = BootV3()
            FileInputStream(fileName).use { fis ->
                val header = BootHeaderV3(fis)
                //info
                ret.info.input = File(fileName).canonicalPath
                ret.info.role = File(fileName).name
                ret.info.json = File(fileName).name.removeSuffix(".img") + ".json"
                ret.info.cmdline = header.cmdline.trim()
                ret.info.headerSize = header.headerSize
                ret.info.headerVersion = header.headerVersion
                ret.info.osVersion = header.osVersion
                ret.info.osPatchLevel = header.osPatchLevel
                ret.info.pageSize = BootHeaderV3.pageSize
                ret.info.signatureSize = header.signatureSize
                //kernel
                ret.kernel.file = Helper.joinPath(workDir, "kernel")
                ret.kernel.size = header.kernelSize
                ret.kernel.position = BootHeaderV3.pageSize
                //ramdisk
                ret.ramdisk.file = Helper.joinPath(workDir, "ramdisk.img")
                ret.ramdisk.size = header.ramdiskSize
                ret.ramdisk.position = ret.kernel.position + header.kernelSize +
                        getPaddingSize(header.kernelSize, BootHeaderV3.pageSize)
                //boot signature
                if (header.signatureSize > 0) {
                    ret.bootSignature.file = Helper.joinPath(workDir, "bootsig")
                    ret.bootSignature.size = header.signatureSize
                    ret.bootSignature.position = ret.ramdisk.position + ret.ramdisk.size +
                            getPaddingSize(header.ramdiskSize, BootHeaderV3.pageSize)
                }
            }
            ret.info.imageSize = File(fileName).length()
            return ret
        }
    }

    data class MiscInfo(
        var input: String = "",
        var role: String = "",
        var json: String = "",
        var headerVersion: Int = 0,
        var headerSize: Int = 0,
        var pageSize: Int = 0,
        var cmdline: String = "",
        var osVersion: String = "",
        var osPatchLevel: String = "",
        var imageSize: Long = 0,
        var signatureSize: Int = 0,
    )

    data class CommArgs(
        var file: String = "",
        var position: Int = 0,
        var size: Int = 0,
    )

    data class RamdiskArgs(
        var file: String = "",
        var position: Int = 0,
        var size: Int = 0,
        var xzFlags: String? = null
    )

    fun pack(): BootV3 {
        if (this.kernel.size > 0) {
            this.kernel.size = File(this.kernel.file).length().toInt()
        }
        if (this.ramdisk.size > 0) {
            if (File(this.ramdisk.file).exists() && !File(workDir, "root").exists()) {
                //do nothing if we have ramdisk.img.gz but no /root
                log.warn("Use prebuilt ramdisk file: ${this.ramdisk.file}")
            } else {
                File(this.ramdisk.file).deleleIfExists()
                File(this.ramdisk.file.replaceFirst("[.][^.]+$", "")).deleleIfExists()
                //TODO: remove cpio in C/C++
                //C.packRootfs(Helper.joinPath($workDir, "root"), this.ramdisk.file, C.parseOsMajor(info.osVersion))
                // enable advance JAVA cpio
                C.packRootfs(Helper.joinPath(workDir, "root"), this.ramdisk.file, this.ramdisk.xzFlags)
            }
            this.ramdisk.size = File(this.ramdisk.file).length().toInt()
        }

        //header
        val intermediateDir = Helper.joinPath(workDir, "intermediate")
        File(intermediateDir).let {
            if (!it.exists()) {
                it.mkdir()
            }
        }
        Helper.setProp("intermediateDir", intermediateDir)
        val clearFile = Helper.joinPath(intermediateDir, this.info.role + ".clear")
        FileOutputStream(clearFile, false).use { fos ->
            //trim bootSig if it's not parsable
            //https://github.com/cfig/Android_boot_image_editor/issues/88
            File(Avb.getJsonFileName(this.bootSignature.file)).let { bootSigJson ->
                if (!bootSigJson.exists()) {
                    errLog.info(
                        "erase unparsable boot signature in header. Refer to https://github.com/cfig/Android_boot_image_editor/issues/88"
                    )
                    this.info.signatureSize = 0
                }
            }
            val encodedHeader = this.toHeader().encode()
            fos.write(encodedHeader)
            fos.write(
                ByteArray(Helper.round_to_multiple(encodedHeader.size, this.info.pageSize) - encodedHeader.size)
            )
        }

        //data
        log.info("Writing data ...")
        //BootV3 should have correct image size
        val bf = ByteBuffer.allocate(maxOf(info.imageSize.toInt(), 64 * 1024 * 1024))
        bf.order(ByteOrder.LITTLE_ENDIAN)
        if (kernel.size > 0) {
            C.writePaddedFile(bf, this.kernel.file, this.info.pageSize)
        }
        if (ramdisk.size > 0) {
            C.writePaddedFile(bf, this.ramdisk.file, this.info.pageSize)
        }
        //write V3 data
        FileOutputStream(clearFile, true).use { fos ->
            fos.write(bf.array(), 0, bf.position())
        }

        //write V4 boot sig
        if (this.info.headerVersion > 3) {
            val bootSigJson = File(Avb.getJsonFileName(this.bootSignature.file))
            var bootSigBytes = ByteArray(this.bootSignature.size)
            if (bootSigJson.exists()) {
                log.warn("V4 BootImage has GKI boot signature")
                val readBackBootSig = mapper.readValue(bootSigJson, AVBInfo::class.java)
                val alg = Algorithms.get(readBackBootSig.header!!.algorithm_type)!!
                //replace new pub key
                readBackBootSig.auxBlob!!.pubkey!!.pubkey = AuxBlob.encodePubKey(alg)
                //update hash and sig
                readBackBootSig.auxBlob!!.hashDescriptors.get(0).update(this.info.role + ".clear")
                bootSigBytes = readBackBootSig.encodePadded()
            }
            if (this.info.signatureSize > 0) {
                //write V4 data
                FileOutputStream(clearFile, true).use { fos ->
                    fos.write(bootSigBytes)
                }
            } else {
                errLog.info("ignore bootsig for v4 boot.img")
            }
        }

        //google way
        val googleClearFile = Helper.joinPath(intermediateDir, this.info.role + ".google")
        this.toCommandLine().addArgument(googleClearFile).let {
            log.info(it.toString())
            DefaultExecutor().execute(it)
        }

        Helper.assertFileEquals(clearFile, googleClearFile)
        File(googleClearFile).delete()
        return this
    }

    fun sign(fileName: String): BootV3 {
        log.warn("XXXX: sign $fileName")
        if (File(Avb.getJsonFileName(info.role)).exists()) {
            Signer.signAVB(
                Helper.joinPath(Helper.prop("intermediateDir")!!, info.role),
                this.info.imageSize,
                String.format(Helper.prop("avbtool")!!, "v1.2")
            )
        } else {
            log.warn("no AVB info found, assume it's clear image")
        }
        if (fileName != info.role) {
            File(Helper.joinPath(Helper.prop("intermediateDir")!!, info.role + ".signed")).copyTo(File(fileName), true)
            log.info("Signed image saved as $fileName")
        } else {
            File(
                Helper.joinPath(
                    Helper.prop("intermediateDir")!!,
                    info.role + ".signed"
                )
            ).copyTo(File(info.role + ".signed"), true)
            log.info("Signed image saved as ${info.role}.signed")
        }
        return this
    }

    private fun toHeader(): BootHeaderV3 {
        return BootHeaderV3(
            kernelSize = kernel.size,
            ramdiskSize = ramdisk.size,
            headerVersion = info.headerVersion,
            osVersion = info.osVersion,
            osPatchLevel = info.osPatchLevel,
            headerSize = info.headerSize,
            cmdline = info.cmdline,
            signatureSize = info.signatureSize
        ).feature67()
    }

    fun extractImages(): BootV3 {
        val workDir = Helper.prop("workDir")
        //info
        mapper.writerWithDefaultPrettyPrinter().writeValue(File(workDir, this.info.json), this)
        //kernel
        if (kernel.size > 0) {
            C.dumpKernel(Helper.Slice(info.input, kernel.position, kernel.size, kernel.file))
        } else {
            log.warn("${this.info.role} has no kernel")
        }
        //ramdisk
        if (ramdisk.size > 0) {
            val fmt = C.dumpRamdisk(
                Helper.Slice(info.role, ramdisk.position, ramdisk.size, ramdisk.file), File(workDir, "root").toString()
            )
            this.ramdisk.file = this.ramdisk.file + ".$fmt"
            if (fmt == "xz") {
                val checkType =
                    ZipHelper.xzStreamFlagCheckTypeToString(ZipHelper.parseStreamFlagCheckType(this.ramdisk.file))
                this.ramdisk.xzFlags = checkType
            }
        }
        //bootsig

        //dump info again
        mapper.writerWithDefaultPrettyPrinter().writeValue(File(workDir, this.info.json), this)
        return this
    }

    fun extractVBMeta(): BootV3 {
        // vbmeta in image
        try {
            log.warn("XXXX: info.output ${info.input}")
            val ai = AVBInfo.parseFrom(Dumpling(info.input)).dumpDefault(info.role)
            if (File("vbmeta.img").exists()) {
                log.warn("Found vbmeta.img, parsing ...")
                VBMetaParser().unpack("vbmeta.img")
            }
        } catch (e: IllegalArgumentException) {
            log.warn(e.message)
            log.warn("failed to parse vbmeta info")
        }

        //GKI 1.0 bootsig
        if (info.signatureSize > 0) {
            log.info("GKI 1.0 signature")
            Dumpling(info.role).readFully(Pair(this.bootSignature.position.toLong(), this.bootSignature.size))
                .let { bootsigData ->
                    File(this.bootSignature.file).writeBytes(bootsigData)
                    if (bootsigData.any { it.toInt() != 0 }) {
                        try {
                            val bootsig = AVBInfo.parseFrom(Dumpling(bootsigData)).dumpDefault(this.bootSignature.file)
                            Avb.verify(bootsig, Dumpling(bootsigData, "bootsig"))
                        } catch (e: IllegalArgumentException) {
                            log.warn("GKI 1.0 boot signature is invalid")
                        }
                    } else {
                        log.warn("GKI 1.0 boot signature has only NULL data")
                    }
                }
            return this
        }

        //GKI 2.0 bootsig
        if (!File(Avb.getJsonFileName(info.role)).exists()) {
            log.info("no AVB info found in ${info.role}")
            return this
        }
        log.info("probing 16KB boot signature ...")
        val mainBlob = ObjectMapper().readValue(
            File(Avb.getJsonFileName(info.role)),
            AVBInfo::class.java
        )
        val bootSig16kData =
            Dumpling(Dumpling(info.input).readFully(Pair(mainBlob.footer!!.originalImageSize - 16 * 1024, 16 * 1024)))
        try {
            val blob1 = AVBInfo.parseFrom(bootSig16kData)
                .also { check(it.auxBlob!!.hashDescriptors[0].partition_name == "boot") }
                .also { it.dumpDefault("sig.boot") }
            val blob2 =
                AVBInfo.parseFrom(Dumpling(bootSig16kData.readFully(blob1.encode().size until bootSig16kData.getLength())))
                    .also { check(it.auxBlob!!.hashDescriptors[0].partition_name == "generic_kernel") }
                    .also { it.dumpDefault("sig.kernel") }
            val gkiAvbData = bootSig16kData.readFully(blob1.encode().size until bootSig16kData.getLength())
            File(workDir, "kernel.img").let { gki ->
                File(workDir, "kernel").copyTo(gki)
                System.setProperty("more", workDir)
                Avb.verify(blob2, Dumpling(gkiAvbData))
                gki.delete()
            }
            log.info(blob1.auxBlob!!.hashDescriptors[0].partition_name)
            log.info(blob2.auxBlob!!.hashDescriptors[0].partition_name)
        } catch (e: IllegalArgumentException) {
            log.warn("can not find boot signature: " + e.message)
        }

        return this
    }

    fun printUnpackSummary(): BootV3 {
        val prints: MutableList<Pair<String, String>> = mutableListOf()
        val workDir = Helper.prop("workDir")
        val tableHeader = AsciiTable().apply {
            addRule()
            addRow("What", "Where")
            addRule()
        }
        val tab = AsciiTable().let {
            it.addRule()
            it.addRow("image info", Helper.joinPath(workDir!!, info.role.removeSuffix(".img") + ".json"))
            prints.add(Pair("image info", Helper.joinPath(workDir, info.role.removeSuffix(".img") + ".json")))
            it.addRule()
            if (this.kernel.size > 0) {
                it.addRow("kernel", this.kernel.file)
                prints.add(Pair("kernel", this.kernel.file))
                File(Helper.joinPath(workDir, Helper.prop("kernelVersionStem")!!)).let { kernelVersionFile ->
                    log.warn("XXXX: kernelVersionFile ${kernelVersionFile.path}")
                    if (kernelVersionFile.exists()) {
                        it.addRow("\\-- version " + kernelVersionFile.readLines().toString(), kernelVersionFile.path)
                        prints.add(
                            Pair(
                                "\\-- version " + kernelVersionFile.readLines().toString(),
                                kernelVersionFile.path
                            )
                        )
                    }
                }
                File(Helper.joinPath(workDir, Helper.prop("kernelConfigStem")!!)).let { kernelConfigFile ->
                    log.warn("XXXX: kernelConfigFile ${kernelConfigFile.path}")
                    if (kernelConfigFile.exists()) {
                        it.addRow("\\-- config", kernelConfigFile.path)
                        prints.add(Pair("\\-- config", kernelConfigFile.path))
                    }
                }
                it.addRule()
            }
            if (this.ramdisk.size > 0) {
                //fancy
                it.addRow("ramdisk", this.ramdisk.file)
                it.addRow("\\-- extracted ramdisk rootfs", Helper.joinPath(workDir, "root"))
                it.addRule()
                //basic
                prints.add(Pair("ramdisk", this.ramdisk.file))
                prints.add(Pair("\\-- extracted ramdisk rootfs", Helper.joinPath(workDir, "root")))
            }
            if (this.info.signatureSize > 0) {
                it.addRow("GKI signature 1.0", this.bootSignature.file)
                prints.add(Pair("GKI signature 1.0", this.bootSignature.file))
                File(Avb.getJsonFileName(this.bootSignature.file)).let { jsFile ->
                    it.addRow("\\-- decoded boot signature", if (jsFile.exists()) jsFile.path else "N/A")
                    prints.add(Pair("\\-- decoded boot signature", if (jsFile.exists()) jsFile.path else "N/A"))
                    if (jsFile.exists()) {
                        it.addRow("\\------ signing key", Avb.inspectKey(mapper.readValue(jsFile, AVBInfo::class.java)))
                        prints.add(
                            Pair(
                                "\\------ signing key",
                                Avb.inspectKey(mapper.readValue(jsFile, AVBInfo::class.java))
                            )
                        )
                    }
                }
                it.addRule()
            }

            //GKI signature 2.0
            File(Avb.getJsonFileName("sig.boot")).let { jsonFile ->
                if (jsonFile.exists()) {
                    it.addRow("GKI signature 2.0", this.bootSignature.file)
                    it.addRow("\\-- boot", jsonFile.path)
                    it.addRow("\\------ signing key", Avb.inspectKey(mapper.readValue(jsonFile, AVBInfo::class.java)))
                    //basic
                    prints.add(Pair("GKI signature 2.0", this.bootSignature.file))
                    prints.add(Pair("\\-- boot", jsonFile.path))
                    prints.add(
                        Pair(
                            "\\------ signing key",
                            Avb.inspectKey(mapper.readValue(jsonFile, AVBInfo::class.java))
                        )
                    )
                }
            }
            File(Avb.getJsonFileName("sig.kernel")).let { jsonFile ->
                if (jsonFile.exists()) {
                    val readBackAvb = mapper.readValue(jsonFile, AVBInfo::class.java)
                    it.addRow("\\-- kernel", jsonFile.path)
                    it.addRow("\\------ signing key", Avb.inspectKey(readBackAvb))
                    it.addRule()
                    //basic
                    prints.add(Pair("\\-- kernel", jsonFile.path))
                    prints.add(Pair("\\------ signing key", Avb.inspectKey(readBackAvb)))
                }
            }

            //AVB info
            Avb.getJsonFileName(info.role).let { jsonFile ->
                it.addRow("AVB info", if (File(jsonFile).exists()) jsonFile else "NONE")
                prints.add(Pair("AVB info", if (File(jsonFile).exists()) jsonFile else "NONE"))
                if (File(jsonFile).exists()) {
                    mapper.readValue(File(jsonFile), AVBInfo::class.java).let { ai ->
                        it.addRow("\\------ signing key", Avb.inspectKey(ai))
                        prints.add(Pair("\\------ signing key", Avb.inspectKey(ai)))
                    }
                }
            }
            it.addRule()
            it
        }
        val tabVBMeta = AsciiTable().let {
            if (File("vbmeta.img").exists()) {
                it.addRule()
                it.addRow("vbmeta.img", Avb.getJsonFileName("vbmeta.img"))
                it.addRule()
                "\n" + it.render()
                //basic
                prints.add(Pair("vbmeta.img", Avb.getJsonFileName("vbmeta.img")))
            } else {
                ""
            }
        }
        if (EnvironmentVerifier().isWindows) {
            log.info("\n" + Common.table2String(prints))
        } else {
            log.info(
                "\n\t\t\tUnpack Summary of ${info.role}\n{}\n{}{}",
                tableHeader.render(), tab.render(), tabVBMeta
            )
        }
        return this
    }

    fun printPackSummary(fileName: String): BootV3 {
        Common.printPackSummary(fileName)
        return this
    }

    fun updateVbmeta(): BootV3 {
        Avb.updateVbmeta(info.role)
        return this
    }

    private fun toCommandLine(): CommandLine {
        val cmdPrefix = if (EnvironmentVerifier().isWindows) "python " else ""
        return CommandLine.parse(cmdPrefix + Helper.prop("mkbootimg")).let { ret ->
            ret.addArgument("--header_version")
            ret.addArgument(info.headerVersion.toString())
            if (kernel.size > 0) {
                ret.addArgument("--kernel")
                ret.addArgument(this.kernel.file)
            }
            if (ramdisk.size > 0) {
                ret.addArgument("--ramdisk")
                ret.addArgument(this.ramdisk.file)
            }
            if (info.cmdline.isNotBlank()) {
                ret.addArgument(" --cmdline ")
                ret.addArgument(info.cmdline.trim(), false)
            }
            if (info.osVersion.isNotBlank()) {
                ret.addArgument(" --os_version")
                ret.addArgument(info.osVersion)
            }
            if (info.osPatchLevel.isNotBlank()) {
                ret.addArgument(" --os_patch_level")
                ret.addArgument(info.osPatchLevel)
            }
            if (this.bootSignature.size > 0 && File(Avb.getJsonFileName(this.bootSignature.file)).exists()) {
                val origSig = mapper.readValue(File(Avb.getJsonFileName(this.bootSignature.file)), AVBInfo::class.java)
                val alg = Algorithms.get(origSig.header!!.algorithm_type)!!
                ret.addArgument("--gki_signing_algorithm").addArgument(alg.name)
                ret.addArgument("--gki_signing_key").addArgument(alg.defaultKey)
                ret.addArgument("--gki_signing_avbtool_path")
                    .addArgument(String.format(Helper.prop("avbtool")!!, "v1.2"))
            }
            ret.addArgument(" --id ")
            ret.addArgument(" --output ")
            //ret.addArgument("boot.img" + ".google")

            log.debug("To Commandline: $ret")
            ret
        }
    }
}
