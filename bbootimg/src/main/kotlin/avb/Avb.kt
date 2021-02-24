package cfig

import avb.AVBInfo
import avb.alg.Algorithms
import avb.blob.AuthBlob
import avb.blob.AuxBlob
import avb.blob.Footer
import avb.blob.Header
import avb.desc.*
import cfig.helper.Helper
import cfig.helper.Helper.Companion.paddingWith
import cfig.helper.KeyHelper
import cfig.io.Struct3
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

@OptIn(ExperimentalUnsignedTypes::class)
class Avb {
    private val MAX_VBMETA_SIZE = 64 * 1024
    private val MAX_FOOTER_SIZE = 4096
    private val BLOCK_SIZE = 4096
    private val DEBUG = false

    //migrated from: avbtool::Avb::addHashFooter
    fun addHashFooter(
        image_file: String,
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

        val vbmetaBlob = packVbMeta(newAvbInfo)
        log.debug("vbmeta_blob: " + Helper.toHexString(vbmetaBlob))
        if (DEBUG) {
            Helper.dumpToFile("hashDescriptor.vbmeta.blob", vbmetaBlob)
        }

        // image + padding
        val imgPaddingNeeded = Helper.round_to_multiple(newImageSize, BLOCK_SIZE) - newImageSize

        // + vbmeta + padding
        val vbmetaOffset = newImageSize + imgPaddingNeeded
        val vbmetaBlobWithPadding = vbmetaBlob.paddingWith(BLOCK_SIZE.toUInt())

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
        assert(partition_size == File(image_file).length()) { "generated file size mismatch" }
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

    fun parseVbMeta(image_file: String, dumpFile: Boolean = true): AVBInfo {
        log.info("parsing $image_file ...")
        val jsonFile = getJsonFileName(image_file)
        var footer: Footer? = null
        var vbMetaOffset: Long = 0
        // footer
        FileInputStream(image_file).use { fis ->
            fis.skip(File(image_file).length() - Footer.SIZE)
            try {
                footer = Footer(fis)
                vbMetaOffset = footer!!.vbMetaOffset
                log.info("$image_file: $footer")
            } catch (e: IllegalArgumentException) {
                log.info("image $image_file has no AVB Footer")
            }
        }

        // header
        var vbMetaHeader: Header
        FileInputStream(image_file).use { fis ->
            fis.skip(vbMetaOffset)
            vbMetaHeader = Header(fis)
        }
        log.debug(vbMetaHeader.toString())
        log.debug(ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(vbMetaHeader))

        val authBlockOffset = vbMetaOffset + Header.SIZE
        val auxBlockOffset = authBlockOffset + vbMetaHeader.authentication_data_block_size
        val descStartOffset = auxBlockOffset + vbMetaHeader.descriptors_offset

        val ai = AVBInfo(vbMetaHeader, null, AuxBlob(), footer)

        // Auth blob
        if (vbMetaHeader.authentication_data_block_size > 0) {
            FileInputStream(image_file).use { fis ->
                fis.skip(vbMetaOffset)
                fis.skip(Header.SIZE.toLong())
                fis.skip(vbMetaHeader.hash_offset)
                val ba = ByteArray(vbMetaHeader.hash_size.toInt())
                fis.read(ba)
                log.debug("Parsed Auth Hash (Header & Aux Blob): " + Hex.encodeHexString(ba))
                val bb = ByteArray(vbMetaHeader.signature_size.toInt())
                fis.read(bb)
                log.debug("Parsed Auth Signature (of hash): " + Hex.encodeHexString(bb))

                ai.authBlob = AuthBlob()
                ai.authBlob!!.offset = authBlockOffset
                ai.authBlob!!.size = vbMetaHeader.authentication_data_block_size
                ai.authBlob!!.hash = Hex.encodeHexString(ba)
                ai.authBlob!!.signature = Hex.encodeHexString(bb)
            }
        }

        // aux - desc
        var descriptors: List<Any>
        if (vbMetaHeader.descriptors_size > 0) {
            FileInputStream(image_file).use { fis ->
                fis.skip(descStartOffset)
                descriptors = UnknownDescriptor.parseDescriptors2(fis, vbMetaHeader.descriptors_size)
            }
            descriptors.forEach {
                log.debug(it.toString())
                when (it) {
                    is PropertyDescriptor -> {
                        ai.auxBlob!!.propertyDescriptor.add(it)
                    }
                    is HashDescriptor -> {
                        ai.auxBlob!!.hashDescriptors.add(it)
                    }
                    is KernelCmdlineDescriptor -> {
                        ai.auxBlob!!.kernelCmdlineDescriptor.add(it)
                    }
                    is HashTreeDescriptor -> {
                        ai.auxBlob!!.hashTreeDescriptor.add(it)
                    }
                    is ChainPartitionDescriptor -> {
                        ai.auxBlob!!.chainPartitionDescriptor.add(it)
                    }
                    is UnknownDescriptor -> {
                        ai.auxBlob!!.unknownDescriptors.add(it)
                    }
                    else -> {
                        throw IllegalArgumentException("invalid descriptor: $it")
                    }
                }
            }
        }
        // aux - pubkey
        if (vbMetaHeader.public_key_size > 0) {
            ai.auxBlob!!.pubkey = AuxBlob.PubKeyInfo()
            ai.auxBlob!!.pubkey!!.offset = vbMetaHeader.public_key_offset
            ai.auxBlob!!.pubkey!!.size = vbMetaHeader.public_key_size

            FileInputStream(image_file).use { fis ->
                fis.skip(auxBlockOffset)
                fis.skip(vbMetaHeader.public_key_offset)
                ai.auxBlob!!.pubkey!!.pubkey = ByteArray(vbMetaHeader.public_key_size.toInt())
                fis.read(ai.auxBlob!!.pubkey!!.pubkey)
                log.debug("Parsed Pub Key: " + Hex.encodeHexString(ai.auxBlob!!.pubkey!!.pubkey))
            }
        }
        // aux - pkmd
        if (vbMetaHeader.public_key_metadata_size > 0) {
            ai.auxBlob!!.pubkeyMeta = AuxBlob.PubKeyMetadataInfo()
            ai.auxBlob!!.pubkeyMeta!!.offset = vbMetaHeader.public_key_metadata_offset
            ai.auxBlob!!.pubkeyMeta!!.size = vbMetaHeader.public_key_metadata_size

            FileInputStream(image_file).use { fis ->
                fis.skip(auxBlockOffset)
                fis.skip(vbMetaHeader.public_key_metadata_offset)
                ai.auxBlob!!.pubkeyMeta!!.pkmd = ByteArray(vbMetaHeader.public_key_metadata_size.toInt())
                fis.read(ai.auxBlob!!.pubkeyMeta!!.pkmd)
                log.debug("Parsed Pub Key Metadata: " + Helper.toHexString(ai.auxBlob!!.pubkeyMeta!!.pkmd))
            }
        }

        //FIXME
        val declaredAlg = Algorithms.get(ai.header!!.algorithm_type)
        if (declaredAlg!!.public_key_num_bytes > 0) {
            if (AuxBlob.encodePubKey(declaredAlg).contentEquals(ai.auxBlob!!.pubkey!!.pubkey)) {
                log.warn("VERIFY: vbmeta is signed with the same key as us")
                val calcHash =
                    AuthBlob.calcHash(ai.header!!.encode(), ai.auxBlob!!.encode(declaredAlg), declaredAlg.name)
                val calcSig = AuthBlob.calcSignature(calcHash, declaredAlg.name)
                if (Helper.toHexString(calcHash) != ai.authBlob!!.hash) {
                    log.error("calculated AuthBlob hash mismatch")
                    throw IllegalArgumentException("calculated AuthBlob hash mismatch")
                } else {
                    log.info("VERIFY: AuthBlob hash matches")
                }
                if (Helper.toHexString(calcSig) != ai.authBlob!!.signature) {
                    log.error("calculated AuthBlob signature mismatch")
                    throw IllegalArgumentException("calculated AuthBlob signature mismatch")
                } else {
                    log.info("VERIFY: AuthBlob signature matches")
                }
            } else {
                val custPubKey = KeyHelper.decodeRSAkey(ai.auxBlob!!.pubkey!!.pubkey)
                log.warn("VERIFY: vbmeta is signed with different key as us")
                log.debug("modulus  :" + custPubKey.modulus)
                log.debug("exponent :" + custPubKey.publicExponent)
            }
            //FIXME
            ai.auxBlob!!.pubkey
        } else {
            log.debug("no key for current algorithm")
        }
        //FIXME

        if (dumpFile) {
            ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(File(jsonFile), ai)
            log.info("vbmeta info of [$image_file] has been analyzed")
            log.info("vbmeta info written to $jsonFile")
        } else {
            log.warn("vbmeta info of [$image_file] has been analyzed, no dummping")
        }

        return ai
    }

    private fun packVbMeta(info: AVBInfo? = null, image_file: String? = null): ByteArray {
        val ai = info ?: ObjectMapper().readValue(File(getJsonFileName(image_file!!)), AVBInfo::class.java)
        val alg = Algorithms.get(ai.header!!.algorithm_type)!!

        //3 - whole aux blob
        val auxBlob = ai.auxBlob?.encode(alg) ?: byteArrayOf()

        //1 - whole header blob
        val headerBlob = ai.header!!.apply {
            auxiliary_data_block_size = auxBlob.size.toLong()
            authentication_data_block_size = Helper.round_to_multiple(
                (alg.hash_num_bytes + alg.signature_num_bytes).toLong(), 64
            )

            descriptors_offset = 0
            descriptors_size = ai.auxBlob?.descriptorSize?.toLong() ?: 0

            hash_offset = 0
            hash_size = alg.hash_num_bytes.toLong()

            signature_offset = alg.hash_num_bytes.toLong()
            signature_size = alg.signature_num_bytes.toLong()

            public_key_offset = descriptors_size
            public_key_size = AuxBlob.encodePubKey(alg).size.toLong()

            public_key_metadata_size = ai.auxBlob!!.pubkeyMeta?.pkmd?.size?.toLong() ?: 0L
            public_key_metadata_offset = public_key_offset + public_key_size
            log.info("pkmd size: $public_key_metadata_size, pkmd offset : $public_key_metadata_offset")
        }.encode()

        //2 - auth blob
        val authBlob = AuthBlob.createBlob(headerBlob, auxBlob, alg.name)

        return Helper.join(headerBlob, authBlob, auxBlob)
    }

    fun packVbMetaWithPadding(image_file: String? = null, info: AVBInfo? = null) {
        val rawBlob = packVbMeta(info, image_file)
        val paddingSize = Helper.round_to_multiple(rawBlob.size.toLong(), BLOCK_SIZE) - rawBlob.size
        val paddedBlob = Helper.join(rawBlob, Struct3("${paddingSize}x").pack(null))
        log.info("raw vbmeta size ${rawBlob.size}, padding size $paddingSize, total blob size ${paddedBlob.size}")
        log.info("Writing padded vbmeta to file: $image_file.signed")
        Files.write(Paths.get("$image_file.signed"), paddedBlob, StandardOpenOption.CREATE)
    }

    companion object {
        private val log = LoggerFactory.getLogger(Avb::class.java)
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

        fun verifyAVBIntegrity(fileName: String, avbtool: String) {
            val cmdline = "python $avbtool verify_image --image $fileName"
            log.info(cmdline)
            try {
                DefaultExecutor().execute(CommandLine.parse(cmdline))
            } catch (e: Exception) {
                throw IllegalArgumentException("$fileName failed integrity check by \"$cmdline\"")
            }
        }

        fun updateVbmeta(fileName: String) {
            if (File("vbmeta.img").exists()) {
                log.info("Updating vbmeta.img side by side ...")
                val partitionName =
                    ObjectMapper().readValue(File(getJsonFileName(fileName)), AVBInfo::class.java).let {
                        it.auxBlob!!.hashDescriptors.get(0).partition_name
                    }
                val newHashDesc = Avb().parseVbMeta("$fileName.signed", dumpFile = false)
                assert(newHashDesc.auxBlob!!.hashDescriptors.size == 1)
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
                        log.warn("main vbmeta doesn't have $partitionName hashDescriptor, skip")
                    } else {
                        val hd = newHashDesc.auxBlob!!.hashDescriptors.get(0).apply { this.sequence = seq }
                        this.auxBlob!!.hashDescriptors.add(hd)
                        Avb().packVbMetaWithPadding("vbmeta.img", this)
                        log.info("Updating vbmeta.img side by side (partition=$partitionName, seq=$seq) done")
                    }
                }
            } else {
                log.info("no companion vbmeta.img")
            }
        }
    }
}
