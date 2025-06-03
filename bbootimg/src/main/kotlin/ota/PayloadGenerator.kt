// Copyright 2022-2023 yuyezhong@gmail.com
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
import cfig.helper.Helper.Companion.check_call
import cfig.helper.ZipHelper.Companion.dumpEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.*
import kotlin.io.path.Path
import kotlin.system.exitProcess

@OptIn(ExperimentalUnsignedTypes::class)
class PayloadGenerator {
    private val log = LoggerFactory.getLogger(PayloadGenerator::class.java)
    val workDir = "build/staging_ota"
    val signedPayload = "$workDir/signed-payload.bin"
    val propertiesFile = "$workDir/payload-properties.txt"

    fun generate(maxTs: String, targetFile: String, payload: String) {
        ("brillo_update_payload generate" +
                " --max_timestamp $maxTs" +
                " --target_image $targetFile" +
                " --payload $payload").check_call()
    }

    fun sign(inSigner: PayloadSigner, options: OtaOptions) {
        // 1. Generate hashes of the payload and metadata files
        ("brillo_update_payload hash" +
                " --unsigned_payload $workDir/payload.bin" +
                " --signature_size " + inSigner.keySize +
                " --metadata_hash_file $workDir/meta.hash" +
                " --payload_hash_file $workDir/payload.hash").check_call()

        // 2. Sign the hashes.
        inSigner.sign("$workDir/meta.hash", "$workDir/signed-meta.hash")
        inSigner.sign("$workDir/payload.hash", "$workDir/signed-payload.hash")

        // 3. Insert the signatures back into the payload file.
        ("brillo_update_payload sign" +
                " --unsigned_payload $workDir/payload.bin" +
                " --payload $signedPayload" +
                " --signature_size " + inSigner.keySize +
                " --metadata_signature_file $workDir/signed-meta.hash" +
                " --payload_signature_file $workDir/signed-payload.hash").check_call()

        // 4. Dump the signed payload properties.
        ("brillo_update_payload properties" +
                " --payload $signedPayload" +
                " --properties_file $propertiesFile").check_call()

        // 5.
        if (options.wipe_user_data) {
            FileOutputStream(propertiesFile, true).use {
                it.write("POWERWASH=1\n".toByteArray())
            }
        }
        options.include_secondary.let { includeSec ->
            if (includeSec) {
                FileOutputStream(propertiesFile, true).use {
                    it.write("SWITCH_SLOT_ON_REBOOT=0\n".toByteArray())
                }
            }
        }
    }

    fun tryToDumpEntry(inputFile: ZipFile, entryItem: String, outFile: String) {
        val entry = inputFile.getEntry(entryItem)
        if (entry != null) {
            inputFile.dumpEntry(entry.name, File(outFile))
        } else {
            log.info("$entryItem not found")
        }
    }

    fun generateMine(maxTs: String, inTargetFile: String, payload: String, infoDict: Properties) {
        val targetFile = ZipFile(inTargetFile)
        val abPartitions =
            String(targetFile.getInputStream(targetFile.getEntry("META/ab_partitions.txt")).readBytes())
                .lines().filter { it.isNotBlank() }
        log.info("Dumping ${abPartitions.size} images from target file ...")

        abPartitions.forEach { part ->
            val partEntryName = listOfNotNull(
                targetFile.getEntry("IMAGES/$part.img"), targetFile.getEntry("RADIO/$part.img")
            )
                .let { parts ->
                    if (parts.size != 1) {
                        log.error("Found multiple images for partition $part")
                        exitProcess(1)
                    }
                    parts[0].name
                }
            //dump image
            targetFile.dumpEntry(partEntryName, File("$workDir/$part.img"))

            run {//unsparse image
                Struct(">I").unpack(FileInputStream("$workDir/$part.img")).let { fileHeader ->
                    if (fileHeader[0] as UInt == 0x3aff26ed.toUInt()) {
                        log.debug("$part is sparse, convert to raw image")
                        "simg2img $workDir/$part.img $workDir/tmp.img".check_call()
                        Files.move(Path("$workDir/tmp.img"), Path("$workDir/$part.img"))
                    }
                }
            }

            run {//dump map file
                val mapFile = targetFile.getEntry(partEntryName.replace(".img", ".map"))
                if (mapFile != null) {
                    log.debug("$part.map found, dump it to $workDir/$part.map")
                    targetFile.dumpEntry(mapFile.name, File("$workDir/$part.map"))
                } else {
                    log.debug("$part.map not found")
                }
            }
            File("$workDir/$part.img").let { partFile ->
                val partSize = partFile.length()
                if (partSize % 4096 != 0L) {
                    log.info("Padding $workDir/$part.img ...")
                    Files.write(
                        Paths.get("$workDir/$part.img"),
                        ByteArray(4096 - (partSize % 4096).toInt()),
                        StandardOpenOption.APPEND
                    )
                }
            }
        }
        targetFile.dumpEntry("META/postinstall_config.txt", File("$workDir/postinstall_config.txt"))
        targetFile.dumpEntry("META/dynamic_partitions_info.txt", File("$workDir/dynamic_partitions_info.txt"))
        tryToDumpEntry(targetFile, "META/apex_info.pb", "$workDir/apex_info.pb")
        targetFile.close()

        data class DeltaGenParam(
            var partitionNames: String = "",
            var newImages: String = "",
            var newMapFiles: String = "",
            var newPostInstallConfig: String = "",
            var dynamicPartitionInfo: String = "",
            var apexInfoFile: String = "",
            var partitionTimeStamps: String = "",
        )

        //partition timestamps
        val pTs: MutableList<Pair<String, String>> = mutableListOf()
        Common.PARTITIONS_WITH_BUILD_PROP.forEach { it ->
            val item: Pair<String, String?> = Pair(it,
                when (it) {
                    "boot" -> {
                        log.info("boot:" + infoDict.get("$it.build.prop") as Properties)
                        (infoDict.get("$it.build.prop") as Properties).getProperty("ro.${it}image.build.date.utc")
                    }
                    else -> (infoDict.get("$it.build.prop") as Properties).getProperty("ro.${it}.build.date.utc")
                })
            if (item.second != null) {
                pTs.add(item as Pair<String, String>)
            }
        }

        val dp = DeltaGenParam().apply {
            partitionNames = abPartitions.reduce { acc, s -> "$acc:$s" }
            newImages = abPartitions.map { "$workDir/$it.img" }.reduce { acc, s -> "$acc:$s" }
            newMapFiles = abPartitions
                .map { if (File("$workDir/$it.map").exists()) "$workDir/$it.map" else "" }
                .reduce { acc, s -> "$acc:$s" }
            newPostInstallConfig = "$workDir/postinstall_config.txt"
            dynamicPartitionInfo = "$workDir/dynamic_partitions_info.txt"
            if (File("$workDir/apex_info.pb").exists()) {
                apexInfoFile = "$workDir/apex_info.pb"
            }
            partitionTimeStamps = pTs.map { it.first + ":" + it.second }.reduce { acc, s -> "$s,$acc" }
        }

        ("delta_generator" +
                " --out_file=$payload" +
                " --partition_names=${dp.partitionNames}" +
                " --new_partitions=${dp.newImages}" +
                " --new_mapfiles=${dp.newMapFiles}" +
                " --major_version=2" +
                " --max_timestamp=$maxTs" +
                " --partition_timestamps=${dp.partitionTimeStamps}" +
                " --new_postinstall_config_file=${dp.newPostInstallConfig}" +
                " --dynamic_partition_info_file=${dp.dynamicPartitionInfo}" +
                if (dp.apexInfoFile.isNotBlank()) " --apex_info_file=${dp.apexInfoFile}" else ""
                ).check_call()
    }

    fun signMine(inSigner: PayloadSigner, options: OtaOptions) {
        //1: hash and meta of payload
        ("delta_generator" +
                " --in_file=$workDir/payload.bin.mine" +
                " --signature_size=${inSigner.keySize}" +
                " --out_hash_file=$workDir/payload.hash.mine" +
                " --out_metadata_hash_file=$workDir/meta.hash.mine").check_call()
        //Helper.assertFileEquals("$workDir/meta.hash", "$workDir/meta.hash.mine")
        //Helper.assertFileEquals("$workDir/payload.hash", "$workDir/payload.hash.mine")

        //2: sign hash and meta
        inSigner.sign("$workDir/meta.hash.mine", "$workDir/signed-meta.hash.mine")
        inSigner.sign("$workDir/payload.hash.mine", "$workDir/signed-payload.hash.mine")
        //Helper.assertFileEquals("$workDir/signed-meta.hash", "$workDir/signed-meta.hash.mine")
        //Helper.assertFileEquals("$workDir/payload.hash", "$workDir/payload.hash.mine")

        //3: hash, meta, payload.bin -> signed-payload.bin
        ("delta_generator" +
                " --in_file=$workDir/payload.bin.mine" +
                " --signature_size=" + inSigner.keySize +
                " --payload_signature_file=$workDir/signed-payload.hash.mine" +
                " --metadata_signature_file=$workDir/signed-meta.hash.mine" +
                " --out_file=$signedPayload.mine").check_call()
        //Helper.assertFileEquals(signedPayload, "$signedPayload.mine")

        //4: payload-properties.txt
        ("delta_generator" +
                " --in_file=$signedPayload.mine" +
                " --properties_file=$propertiesFile.mine").check_call()
        //Helper.assertFileEquals(propertiesFile, "$propertiesFile.mine")

        // 5: payload-properties.txt appending
        if (options.wipe_user_data) {
            FileOutputStream(propertiesFile, true).use {
                it.write("POWERWASH=1\n".toByteArray())
            }
        }
        if (options.include_secondary) {
            FileOutputStream(propertiesFile, true).use {
                it.write("SWITCH_SLOT_ON_REBOOT=0\n".toByteArray())
            }
        }
    }
}
