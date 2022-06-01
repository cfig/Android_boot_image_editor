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

package cfig

import avb.AVBInfo
import avb.alg.Algorithms
import avb.blob.AuthBlob
import avb.blob.AuxBlob
import avb.blob.Footer
import avb.blob.Header
import avb.desc.HashDescriptor
import cfig.helper.CryptoHelper
import cfig.helper.Helper
import cfig.helper.Helper.Companion.paddingWith
import cfig.helper.Helper.DataSrc
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.commons.codec.binary.Hex
import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

class Avb {
    private val MAX_VBMETA_SIZE = 64 * 1024
    private val MAX_FOOTER_SIZE = 4096

    //migrated from: avbtool::Avb::addHashFooter
    fun addHashFooter(
        image_file: String, //file to be hashed and signed
        partition_size: Long, //aligned by Avb::BLOCK_SIZE
        partition_name: String,
        newAvbInfo: AVBInfo
    ) {
        log.info("addHashFooter($image_file) ...")

        imageSizeCheck(partition_size, image_file)

        //truncate AVB footer if there is. Then addHashFooter() is idempotent
        trimFooter(image_file)
        val newImageSize = File(image_file).length()

        //VBmeta blob: update hash descriptor
        newAvbInfo.apply {
            val itr = this.auxBlob!!.hashDescriptors.iterator()
            var hd = HashDescriptor()
            while (itr.hasNext()) {//remove previous hd entry
                val itrValue = itr.next()
                if (itrValue.partition_name == partition_name) {
                    itr.remove()
                    hd = itrValue
                }
            }
            //HashDescriptor
            hd.update(image_file)
            log.info("updated hash descriptor:" + Hex.encodeHexString(hd.encode()))
            this.auxBlob!!.hashDescriptors.add(hd)
        }

        // image + padding
        val imgPaddingNeeded = Helper.round_to_multiple(newImageSize.toInt(), BLOCK_SIZE) - newImageSize

        // + vbmeta + padding
        val vbmetaBlob = newAvbInfo.encode()
        val vbmetaOffset = newImageSize + imgPaddingNeeded
        val vbmetaBlobWithPadding = newAvbInfo.encodePadded()

        // + DONT_CARE chunk
        val vbmetaEndOffset = vbmetaOffset + vbmetaBlobWithPadding.size
        val dontCareChunkSize = partition_size - vbmetaEndOffset - 1 * BLOCK_SIZE

        // + AvbFooter + padding
        newAvbInfo.footer!!.apply {
            originalImageSize = newImageSize
            vbMetaOffset = vbmetaOffset
            vbMetaSize = vbmetaBlob.size.toLong()
        }
        log.info(newAvbInfo.footer.toString())
        val footerBlobWithPadding = newAvbInfo.footer!!.encode().paddingWith(BLOCK_SIZE.toUInt(), true)

        FileOutputStream(image_file, true).use { fos ->
            log.info("1/4 Padding image with $imgPaddingNeeded bytes ...")
            fos.write(ByteArray(imgPaddingNeeded.toInt()))

            log.info("2/4 Appending vbmeta (${vbmetaBlobWithPadding.size} bytes)...")
            fos.write(vbmetaBlobWithPadding)

            log.info("3/4 Appending DONT CARE CHUNK ($dontCareChunkSize bytes) ...")
            fos.write(ByteArray(dontCareChunkSize.toInt()))

            log.info("4/4 Appending AVB footer (${footerBlobWithPadding.size} bytes)...")
            fos.write(footerBlobWithPadding)
        }
        check(partition_size == File(image_file).length()) { "generated file size mismatch" }
        log.info("addHashFooter($image_file) done.")
    }

    private fun trimFooter(image_file: String) {
        var footer: Footer? = null
        FileInputStream(image_file).use {
            it.skip(File(image_file).length() - 64)
            try {
                footer = Footer(it)
                log.info("original image $image_file has AVB footer")
            } catch (e: IllegalArgumentException) {
                log.info("original image $image_file doesn't have AVB footer")
            }
        }
        footer?.let {
            FileOutputStream(File(image_file), true).channel.use { fc ->
                log.info(
                    "original image $image_file has AVB footer, " +
                            "truncate it to original SIZE: ${it.originalImageSize}"
                )
                fc.truncate(it.originalImageSize)
            }
        }
    }

    private fun imageSizeCheck(partition_size: Long, image_file: String) {
        //image size sanity check
        val maxMetadataSize = MAX_VBMETA_SIZE + MAX_FOOTER_SIZE
        if (partition_size < maxMetadataSize) {
            throw IllegalArgumentException(
                "Parition SIZE of $partition_size is too small. " +
                        "Needs to be at least $maxMetadataSize"
            )
        }
        val maxImageSize = partition_size - maxMetadataSize
        log.info("max_image_size: $maxImageSize")

        //TODO: typical block size = 4096L, from avbtool::Avb::ImageHandler::block_size
        //since boot.img is not in sparse format, we are safe to hardcode it to 4096L for now
        if (partition_size % BLOCK_SIZE != 0L) {
            throw IllegalArgumentException(
                "Partition SIZE of $partition_size is not " +
                        "a multiple of the image block SIZE 4096"
            )
        }

        val originalFileSize = File(image_file).length()
        if (originalFileSize > maxImageSize) {
            throw IllegalArgumentException(
                "Image size of $originalFileSize exceeds maximum image size " +
                        "of $maxImageSize in order to fit in a partition size of $partition_size."
            )
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(Avb::class.java)
        const val BLOCK_SIZE = 4096
        const val AVB_VERSION_MAJOR = 1
        const val AVB_VERSION_MINOR = 1
        const val AVB_VERSION_SUB = 0

        fun getJsonFileName(image_file: String): String {
            val jsonFile = File(image_file).name.removeSuffix(".img") + ".avb.json"
            return Helper.prop("workDir") + jsonFile
        }

        fun hasAvbFooter(fileName: String): Boolean {
            val expectedBf = "AVBf".toByteArray()
            FileInputStream(fileName).use { fis ->
                fis.skip(File(fileName).length() - 64)
                val bf = ByteArray(4)
                fis.read(bf)
                return bf.contentEquals(expectedBf)
            }
        }

        fun verifyAVBIntegrity(fileName: String, avbtool: String): Boolean {
            val cmdline = "python $avbtool verify_image --image $fileName"
            log.info(cmdline)
            try {
                DefaultExecutor().execute(CommandLine.parse(cmdline))
            } catch (e: Exception) {
                log.error("$fileName failed integrity check by \"$cmdline\"")
                return false
            }
            return true
        }

        fun updateVbmeta(fileName: String) {
            if (File("vbmeta.img").exists()) {
                log.info("Updating vbmeta.img side by side ...")
                val partitionName =
                    ObjectMapper().readValue(File(getJsonFileName(fileName)), AVBInfo::class.java).let {
                        it.auxBlob!!.hashDescriptors.get(0).partition_name
                    }
                //read hashDescriptor from image
                val newHashDesc = AVBInfo.parseFrom(DataSrc("$fileName.signed"))
                check(newHashDesc.auxBlob!!.hashDescriptors.size == 1)
                var seq = -1 //means not found
                //main vbmeta
                ObjectMapper().readValue(File(getJsonFileName("vbmeta.img")), AVBInfo::class.java).apply {
                    val itr = this.auxBlob!!.hashDescriptors.iterator()
                    while (itr.hasNext()) {
                        val itrValue = itr.next()
                        if (itrValue.partition_name == partitionName) {
                            log.info("Found $partitionName in vbmeta, update it")
                            seq = itrValue.sequence
                            itr.remove()
                            break
                        }
                    }
                    if (-1 == seq) {
                        log.warn("main vbmeta doesn't have $partitionName hashDescriptor, won't update vbmeta.img")
                    } else {
                        //add hashDescriptor back to main vbmeta
                        val hd = newHashDesc.auxBlob!!.hashDescriptors.get(0).apply { this.sequence = seq }
                        this.auxBlob!!.hashDescriptors.add(hd)
                        log.info("Writing padded vbmeta to file: vbmeta.img.signed")
                        Files.write(Paths.get("vbmeta.img.signed"), encodePadded(), StandardOpenOption.CREATE)
                        log.info("Updating vbmeta.img side by side (partition=$partitionName, seq=$seq) done")
                    }
                }
            } else {
                log.debug("no companion vbmeta.img")
            }
        }

        fun verify(ai: AVBInfo, image_file: String, parent: String = ""): Array<Any> {
            val ret: Array<Any> = arrayOf(true, "")
            val localParent = parent.ifEmpty { image_file }
            //header
            val rawHeaderBlob = DataSrc(image_file).readFully(Pair(ai.footer?.vbMetaOffset ?: 0, Header.SIZE))
            // aux
            val vbOffset = ai.footer?.vbMetaOffset ?: 0
            //@formatter:off
            val rawAuxBlob = DataSrc(image_file).readFully(
                    Pair(vbOffset + Header.SIZE + ai.header!!.authentication_data_block_size,
                        ai.header!!.auxiliary_data_block_size.toInt()))
            //@formatter:on
            //integrity check
            val declaredAlg = Algorithms.get(ai.header!!.algorithm_type)
            if (declaredAlg!!.public_key_num_bytes > 0) {
                val gkiPubKey = if (declaredAlg.algorithm_type == 1) AuxBlob.encodePubKey(
                    declaredAlg,
                    File("aosp/make/target/product/gsi/testkey_rsa2048.pem").readBytes()
                ) else null
                if (AuxBlob.encodePubKey(declaredAlg).contentEquals(ai.auxBlob!!.pubkey!!.pubkey)) {
                    log.info("VERIFY($localParent): signed with dev key: " + declaredAlg.defaultKey)
                } else if (gkiPubKey.contentEquals(ai.auxBlob!!.pubkey!!.pubkey)) {
                    log.info("VERIFY($localParent): signed with dev GKI key: " + declaredAlg.defaultKey)
                } else {
                    log.info("VERIFY($localParent): signed with release key")
                }
                val calcHash =
                    Helper.join(declaredAlg.padding, AuthBlob.calcHash(rawHeaderBlob, rawAuxBlob, declaredAlg.name))
                val readHash = Helper.join(declaredAlg.padding, Helper.fromHexString(ai.authBlob!!.hash!!))
                if (calcHash.contentEquals(readHash)) {
                    log.info("VERIFY($localParent->AuthBlob): verify hash... PASS")
                    val readPubKey = CryptoHelper.KeyBox.decodeRSAkey(ai.auxBlob!!.pubkey!!.pubkey)
                    val hashFromSig =
                        CryptoHelper.Signer.rawRsa(readPubKey, Helper.fromHexString(ai.authBlob!!.signature!!))
                    if (hashFromSig.contentEquals(readHash)) {
                        log.info("VERIFY($localParent->AuthBlob): verify signature... PASS")
                    } else {
                        ret[0] = false
                        ret[1] = ret[1] as String + " verify signature fail;"
                        log.warn("read=" + Helper.toHexString(readHash) + ", calc=" + Helper.toHexString(calcHash))
                        log.warn("VERIFY($localParent->AuthBlob): verify signature... FAIL")
                    }
                } else {
                    ret[0] = false
                    ret[1] = ret[1] as String + " verify hash fail"
                    log.warn("read=" + ai.authBlob!!.hash!! + ", calc=" + Helper.toHexString(calcHash))
                    log.warn("VERIFY($localParent->AuthBlob): verify hash... FAIL")
                }
            } else {
                log.warn("VERIFY($localParent->AuthBlob): algorithm=[${declaredAlg.name}], no signature, skip")
            }

            val prefixes = setOf(System.getenv("more"), System.getProperty("more")).filterNotNull()
                .map { Paths.get(it).toString() + "/" }.toMutableList().apply { add("") }
            ai.auxBlob!!.chainPartitionDescriptors.forEach {
                val vRet = it.verify(
                    prefixes.map { prefix -> "$prefix${it.partition_name}.img" },
                    image_file + "->Chain[${it.partition_name}]"
                )
                if (vRet[0] as Boolean) {
                    log.info("VERIFY($localParent->Chain[${it.partition_name}]): " + "PASS")
                } else {
                    ret[0] = false
                    ret[1] = ret[1] as String + "; " + vRet[1] as String
                    log.info("VERIFY($localParent->Chain[${it.partition_name}]): " + vRet[1] as String + "... FAIL")
                }
            }

            ai.auxBlob!!.hashDescriptors.forEach {
                val vRet = it.verify(
                    prefixes.map { prefix -> "$prefix${it.partition_name}.img" },
                    image_file + "->HashDescriptor[${it.partition_name}]"
                )
                if (vRet[0] as Boolean) {
                    log.info("VERIFY($localParent->HashDescriptor[${it.partition_name}]): ${it.hash_algorithm} " + "PASS")
                } else {
                    ret[0] = false
                    ret[1] = ret[1] as String + "; " + vRet[1] as String
                    log.info("VERIFY($localParent->HashDescriptor[${it.partition_name}]): ${it.hash_algorithm} " + vRet[1] as String + "... FAIL")
                }
            }

            ai.auxBlob!!.hashTreeDescriptors.forEach {
                val vRet = it.verify(
                    prefixes.map { prefix -> "$prefix${it.partition_name}.img" },
                    image_file + "->HashTreeDescriptor[${it.partition_name}]"
                )
                if (vRet[0] as Boolean) {
                    log.info("VERIFY($localParent->HashTreeDescriptor[${it.partition_name}]): ${it.hash_algorithm} " + "PASS")
                } else {
                    ret[0] = false
                    ret[1] = ret[1] as String + "; " + vRet[1] as String
                    log.info("VERIFY($localParent->HashTreeDescriptor[${it.partition_name}]): ${it.hash_algorithm} " + vRet[1] as String + "... FAIL")
                }
            }

            return ret
        }
    }
}
