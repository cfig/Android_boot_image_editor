// Copyright 2022 yuyezhong@gmail.com
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

package cc.cfig.droid.ota

import cc.cfig.io.Struct
import cfig.helper.CryptoHelper.Hasher
import cfig.helper.Dumpling
import cfig.helper.Helper
import chromeos_update_engine.UpdateMetadata
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.protobuf.ByteString
import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.apache.commons.exec.PumpStreamHandler
import org.slf4j.LoggerFactory
import java.io.*
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class Payload {
    var fileName: String = ""
    var header = PayloadHeader()
    var manifest: UpdateMetadata.DeltaArchiveManifest = UpdateMetadata.DeltaArchiveManifest.newBuilder().build()
    var metaSig: UpdateMetadata.Signatures = UpdateMetadata.Signatures.newBuilder().build()
    var metaSize: Int = 0
    var dataOffset: Long = 0L
    var dataSig: UpdateMetadata.Signatures = UpdateMetadata.Signatures.newBuilder().build()

    companion object {
        private val log = LoggerFactory.getLogger(Payload::class.java)
        val workDir = Helper.prop("payloadDir")

        fun parse(inFileName: String): Payload {
            val ret = Payload()
            ret.fileName = inFileName
            FileInputStream(inFileName).use { fis ->
                ret.header = PayloadHeader(fis)
                ret.metaSize = ret.header.headerSize + ret.header.manifestLen.toInt()
                ret.dataOffset = (ret.metaSize + ret.header.metaSigLen).toLong()
                //manifest
                ret.manifest = ByteArray(ret.header.manifestLen.toInt()).let { buf ->
                    fis.read(buf)
                    UpdateMetadata.DeltaArchiveManifest.parseFrom(buf)
                }
                //meta sig
                ret.metaSig = ByteArray(ret.header.metaSigLen).let { buf2 ->
                    fis.read(buf2)
                    UpdateMetadata.Signatures.parseFrom(buf2)
                }

                //data sig
                if (ret.manifest.hasSignaturesOffset()) {
                    log.debug("payload sig offset = " + ret.manifest.signaturesOffset)
                    log.debug("payload sig size = " + ret.manifest.signaturesSize)
                    fis.skip(ret.manifest.signaturesOffset)
                    ret.dataSig = ByteArray(ret.manifest.signaturesSize.toInt()).let { buf ->
                        fis.read(buf)
                        UpdateMetadata.Signatures.parseFrom(buf)
                    }
                } else {
                    log.warn("payload has no signatures")
                }
            } //end-of-fis

            run {//CHECK_EQ(payload.size(), signatures_offset + manifest.signatures_size())
                val calculatedSize = ret.header.headerSize + ret.header.manifestLen + ret.header.metaSigLen +
                        ret.manifest.signaturesOffset + ret.manifest.signaturesSize
                if (File(inFileName).length() == calculatedSize) {
                    log.info("payload.bin size info check PASS")
                } else {
                    throw IllegalStateException("calculated payload size doesn't match file size")
                }
            }

            val calcMetadataHash =
                Hasher.hash(Dumpling(inFileName), listOf(Pair(0L, ret.metaSize.toLong())), "sha-256")
            log.info("calc meta hash: " + Helper.toHexString(calcMetadataHash))
            val calcPayloadHash = Hasher.hash(
                Dumpling(inFileName), listOf(
                    Pair(0L, ret.metaSize.toLong()),
                    Pair(ret.metaSize.toLong() + ret.header.metaSigLen, ret.manifest.signaturesOffset)
                ), "sha-256"
            )
            check(calcPayloadHash.size == 32)
            log.info("calc payload hash: " + Helper.toHexString(calcPayloadHash))

            val readPayloadSignature = UpdateMetadata.Signatures.parseFrom(
                Helper.readFully(
                    inFileName,
                    ret.dataOffset + ret.manifest.signaturesOffset,
                    ret.manifest.signaturesSize.toInt()
                )
            )
            log.info("Found sig count: " + readPayloadSignature.signaturesCount)
            readPayloadSignature.signaturesList.forEach {
                //pass
                log.info(it.data.toString())
                log.info("sig_data size = " + it.data.toByteArray().size)
                log.info(Helper.toHexString(it.data.toByteArray()))
                Files.write(Paths.get("sig_data"), it.data.toByteArray())
            }

            return ret
        }

        class PayloadVerifier {
            fun getRawHashFromSignature(sig_data: ByteString, pubkey: String, sigHash: ByteArray) {
            }
        }

        fun displaySignatureBlob(sigName: String, sig: UpdateMetadata.Signatures): String {
            return StringBuilder().let { sb ->
                sb.append(String.format("%s signatures: (%d entries)\n", sigName, sig.signaturesCount))
                sig.signaturesList.forEach {
                    sb.append(String.format("  hex_data: (%d bytes)\n", it.data.size()))
                    sb.append("  Data: " + Helper.toHexString(it.data.toByteArray()) + "\n")
                }
                sb
            }.toString()
        }
    }

    fun printInfo() {
        val mi = ManifestInfo(blockSize = this.manifest.blockSize,
            minorVersion = this.manifest.minorVersion,
            maxTimeStamp = this.manifest.maxTimestamp,
            signatureOffset = this.manifest.signaturesOffset,
            signatureSize = this.manifest.signaturesSize,
            partialUpdate = this.manifest.hasPartialUpdate(),
            partsToUpdate = this.manifest.partitionsList.map {
                ManifestInfo.PartitionToUpdate(
                    it.partitionName, it.operationsCount,
                    if (it.hasRunPostinstall()) it.runPostinstall else null,
                    if (it.hasPostinstallPath()) it.postinstallPath else null
                )
            },
            enableSnapshot = this.manifest.dynamicPartitionMetadata.hasSnapshotEnabled(),
            dynamicGroups = this.manifest.dynamicPartitionMetadata.groupsList.map {
                ManifestInfo.DynamicPartGroup(name = it.name, size = it.size, partName = it.partitionNamesList)
            })
        ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(File("$workDir/header.json"), this.header)
        log.info("  header  info dumped to ${workDir}header.json")
        ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(File("$workDir/manifest.json"), mi)
        log.info(" manifest info dumped to ${workDir}manifest.json")

        val signatureFile = "${workDir}signatures.txt"
        FileOutputStream(signatureFile, false).use { fos ->
            fos.writer().use { fWriter ->
                fWriter.write("<Metadata> signatures: offset=" + this.header.manifestLen + ", size=" + this.header.metaSigLen + "\n")
                fWriter.write(Payload.displaySignatureBlob("<Metadata>", this.metaSig))
                fWriter.write("<Payload> signatures: base= offset=" + manifest.signaturesOffset + ", size=" + this.header.metaSigLen + "\n")
                fWriter.write((Payload.displaySignatureBlob("<Payload>", this.dataSig)))
            }
        }
        log.info("signature info dumped to $signatureFile")
    }

    private fun decompress(inBytes: ByteArray, opType: UpdateMetadata.InstallOperation.Type): ByteArray {
        val baosO = ByteArrayOutputStream()
        val baosE = ByteArrayOutputStream()
        val bais = ByteArrayInputStream(inBytes)
        DefaultExecutor().let { exec ->
            exec.streamHandler = PumpStreamHandler(baosO, baosE, bais)
            val cmd = when (opType) {
                UpdateMetadata.InstallOperation.Type.REPLACE_XZ -> CommandLine("xzcat")
                UpdateMetadata.InstallOperation.Type.REPLACE_BZ -> CommandLine("bzcat")
                UpdateMetadata.InstallOperation.Type.REPLACE -> return inBytes
                UpdateMetadata.InstallOperation.Type.ZERO -> {
                    if (inBytes.any { it.toInt() != 0 }) {
                        throw IllegalArgumentException("ZERO is not zero")
                    }
                    log.warn("op type ZERO: ${inBytes.size} bytes")
                    return inBytes
                }

                else -> throw IllegalArgumentException(opType.toString())
            }
            cmd.addArgument("-")
            exec.execute(cmd)
        }
        return baosO.toByteArray()
    }

    private fun unpackInternal(ras: RandomAccessFile, pu: UpdateMetadata.PartitionUpdate, logPrefix: String = "") {
        log.info(String.format("[%s] extracting %13s.img (%d ops)", logPrefix, pu.partitionName, pu.operationsCount))
        FileOutputStream("$workDir/${pu.partitionName}.img").use { outFile ->
            val ops = pu.operationsList.toMutableList().apply {
                sortBy { it.getDstExtents(0).startBlock }
            }
            ops.forEach { op ->
                log.debug(pu.partitionName + ": " + (op.getDstExtents(0).startBlock * this.manifest.blockSize) + ", size=" + op.dataLength + ", type=" + op.type)
                val piece = ByteArray(op.dataLength.toInt()).let {
                    ras.seek(this.dataOffset + op.dataOffset)
                    ras.read(it)
                    it
                }
                outFile.write(decompress(piece, op.type))
            }
        }
    }

    fun setUp() {
        File(workDir).let {
            if (it.exists()) {
                log.info("Removing $workDir")
                it.deleteRecursively()
            }
            log.info("Creating $workDir")
            it.mkdirs()
        }
    }

    fun unpack() {
        RandomAccessFile(this.fileName, "r").use { ras ->
            var currentNum = 1
            val totalNum = this.manifest.partitionsCount
            val parts = this.manifest.partitionsList.map { it.partitionName }
            log.info("There are $totalNum partitions $parts")
            log.info("dumping images to $workDir")
            val partArg = System.getProperty("part", "")
            if (partArg.isNotBlank()) {
                //Usage: gradle unpack -Dpart=vendor_boot
                log.warn("dumping partition [$partArg] only")
                this.manifest.partitionsList
                    .filter { it.partitionName == partArg }
                    .forEach { pu ->
                        unpackInternal(ras, pu, String.format("%2d/%d", currentNum, totalNum))
                        currentNum++
                    }
            } else {
                this.manifest.partitionsList.forEach { pu ->
                    unpackInternal(ras, pu, String.format("%2d/%d", currentNum, totalNum))
                    currentNum++
                }
            }
        }
    }

    data class PayloadHeader(
        var version: Long = 0,
        var manifestLen: Long = 0,
        var metaSigLen: Int = 0,
        var headerSize: Int = 0
    ) {
        private val magic = "CrAU"
        private val FORMAT_STRING = ">4sqq" //magic, version, manifestLen
        private val CHROMEOS_MAJOR_PAYLOAD_VERSION = 1L
        private val BRILLO_MAJOR_PAYLOAD_VERSION = 2L
        val typeOfVersion: String
            get() = when (version) {
                CHROMEOS_MAJOR_PAYLOAD_VERSION -> "chromeOs"
                BRILLO_MAJOR_PAYLOAD_VERSION -> "brillo"
                else -> throw IllegalArgumentException()
            }

        constructor(fis: InputStream) : this() {
            val info = Struct(FORMAT_STRING).unpack(fis)
            check((info[0] as String) == magic) { "${info[0]} is not payload magic" }
            version = info[1] as Long
            manifestLen = info[2] as Long
            headerSize = Struct(FORMAT_STRING).calcSize()

            if (version == BRILLO_MAJOR_PAYLOAD_VERSION) {
                headerSize += Int.SIZE_BYTES
                metaSigLen = Struct(">i").unpack(fis)[0] as Int
            }
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class ManifestInfo(
        var blockSize: Int? = null,
        var minorVersion: Int? = null,
        var maxTimeStamp: Long = 0L,
        var maxTimeReadable: String? = null,
        var partialUpdate: Boolean? = null,
        val signatureOffset: Long? = null,
        val signatureSize: Long? = null,
        var dynamicGroups: List<DynamicPartGroup> = listOf(),
        var enableSnapshot: Boolean? = null,
        var partsToUpdate: List<PartitionToUpdate> = listOf()
    ) {
        init {
            val ldt = Instant.ofEpochMilli(maxTimeStamp * 1000)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
            maxTimeReadable = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(ldt) + " (${ZoneId.systemDefault()})"
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        data class PartitionToUpdate(
            var name: String = "",
            var ops: Int = 0,
            var runPostInstall: Boolean? = null,
            var postInstallPath: String? = null
        )

        data class DynamicPartGroup(
            var name: String = "",
            var size: Long = 0L,
            var partName: List<String> = listOf()
        )
    }
}
