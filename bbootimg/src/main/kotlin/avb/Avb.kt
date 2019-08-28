package cfig

import avb.AVBInfo
import avb.alg.Algorithms
import avb.blob.AuthBlob
import avb.blob.AuxBlob
import avb.blob.Footer
import avb.blob.Header
import avb.desc.*
import cfig.Helper.Companion.paddingWith
import cfig.io.Struct3
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.commons.codec.binary.Hex
import org.junit.Assert
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

@ExperimentalUnsignedTypes
class Avb {
    private val MAX_VBMETA_SIZE = 64 * 1024
    private val MAX_FOOTER_SIZE = 4096
    private val BLOCK_SIZE = 4096

    //migrated from: avbtool::Avb::addHashFooter
    fun addHashFooter(image_file: String,
                      partition_size: Long, //aligned by Avb::BLOCK_SIZE
                      partition_name: String,
                      newAvbInfo: AVBInfo) {
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
        Helper.dumpToFile("hashDescriptor.vbmeta.blob", vbmetaBlob)

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
            originalImageSize = newImageSize.toULong()
            vbMetaOffset = vbmetaOffset.toULong()
            vbMetaSize = vbmetaBlob.size.toULong()
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
        Assert.assertEquals("generated file size mismatch", partition_size, File(image_file).length())
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
                log.info("original image $image_file has AVB footer, " +
                        "truncate it to original SIZE: ${it.originalImageSize}")
                fc.truncate(it.originalImageSize.toLong())
            }
        }
    }

    private fun imageSizeCheck(partition_size: Long, image_file: String) {
        //image size sanity check
        val maxMetadataSize = MAX_VBMETA_SIZE + MAX_FOOTER_SIZE
        if (partition_size < maxMetadataSize) {
            throw IllegalArgumentException("Parition SIZE of $partition_size is too small. " +
                    "Needs to be at least $maxMetadataSize")
        }
        val maxImageSize = partition_size - maxMetadataSize
        log.info("max_image_size: $maxImageSize")

        //TODO: typical block size = 4096L, from avbtool::Avb::ImageHandler::block_size
        //since boot.img is not in sparse format, we are safe to hardcode it to 4096L for now
        if (partition_size % BLOCK_SIZE != 0L) {
            throw IllegalArgumentException("Partition SIZE of $partition_size is not " +
                    "a multiple of the image block SIZE 4096")
        }

        val originalFileSize = File(image_file).length()
        if (originalFileSize > maxImageSize) {
            throw IllegalArgumentException("Image size of $originalFileSize exceeds maximum image size " +
                    "of $maxImageSize in order to fit in a partition size of $partition_size.")
        }
    }

    fun parseVbMeta(image_file: String, dumpFile: Boolean = true): AVBInfo {
        log.info("parsing $image_file ...")
        val jsonFile = getJsonFileName(image_file)
        var footer: Footer? = null
        var vbMetaOffset: ULong = 0U
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
        var vbMetaHeader = Header()
        FileInputStream(image_file).use { fis ->
            fis.skip(vbMetaOffset.toLong())
            vbMetaHeader = Header(fis)
        }
        log.info(vbMetaHeader.toString())
        log.debug(ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(vbMetaHeader))

        val authBlockOffset = vbMetaOffset + Header.SIZE.toUInt()
        val auxBlockOffset = authBlockOffset + vbMetaHeader.authentication_data_block_size
        val descStartOffset = auxBlockOffset + vbMetaHeader.descriptors_offset

        val ai = AVBInfo(vbMetaHeader, null, AuxBlob(), footer)

        // Auth blob
        if (vbMetaHeader.authentication_data_block_size > 0U) {
            FileInputStream(image_file).use { fis ->
                fis.skip(vbMetaOffset.toLong())
                fis.skip(Header.SIZE.toLong())
                fis.skip(vbMetaHeader.hash_offset.toLong())
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
        var descriptors: List<Any> = mutableListOf()
        if (vbMetaHeader.descriptors_size > 0U) {
            FileInputStream(image_file).use { fis ->
                fis.skip(descStartOffset.toLong())
                descriptors = UnknownDescriptor.parseDescriptors2(fis, vbMetaHeader.descriptors_size.toLong())
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
        if (vbMetaHeader.public_key_size > 0U) {
            ai.auxBlob!!.pubkey = AuxBlob.PubKeyInfo()
            ai.auxBlob!!.pubkey!!.offset = vbMetaHeader.public_key_offset.toLong()
            ai.auxBlob!!.pubkey!!.size = vbMetaHeader.public_key_size.toLong()

            FileInputStream(image_file).use { fis ->
                fis.skip(auxBlockOffset.toLong())
                fis.skip(vbMetaHeader.public_key_offset.toLong())
                ai.auxBlob!!.pubkey!!.pubkey = ByteArray(vbMetaHeader.public_key_size.toInt())
                fis.read(ai.auxBlob!!.pubkey!!.pubkey)
                log.debug("Parsed Pub Key: " + Hex.encodeHexString(ai.auxBlob!!.pubkey!!.pubkey))
            }
        }
        // aux - pkmd
        if (vbMetaHeader.public_key_metadata_size > 0U) {
            ai.auxBlob!!.pubkeyMeta = AuxBlob.PubKeyMetadataInfo()
            ai.auxBlob!!.pubkeyMeta!!.offset = vbMetaHeader.public_key_metadata_offset.toLong()
            ai.auxBlob!!.pubkeyMeta!!.size = vbMetaHeader.public_key_metadata_size.toLong()

            FileInputStream(image_file).use { fis ->
                fis.skip(auxBlockOffset.toLong())
                fis.skip(vbMetaHeader.public_key_metadata_offset.toLong())
                ai.auxBlob!!.pubkeyMeta!!.pkmd = ByteArray(vbMetaHeader.public_key_metadata_size.toInt())
                fis.read(ai.auxBlob!!.pubkeyMeta!!.pkmd)
                log.debug("Parsed Pub Key Metadata: " + Helper.toHexString(ai.auxBlob!!.pubkeyMeta!!.pkmd))
            }
        }

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
        val alg = Algorithms.get(ai.header!!.algorithm_type.toInt())!!

        //3 - whole aux blob
        val auxBlob = ai.auxBlob?.encode(alg) ?: byteArrayOf()

        //1 - whole header blob
        val headerBlob = ai.header!!.apply {
            auxiliary_data_block_size = auxBlob.size.toULong()
            authentication_data_block_size = Helper.round_to_multiple(
                    (alg.hash_num_bytes + alg.signature_num_bytes).toLong(), 64).toULong()

            descriptors_offset = 0U
            descriptors_size = ai.auxBlob?.descriptorSize?.toULong() ?: 0U

            hash_offset = 0U
            hash_size = alg.hash_num_bytes.toULong()

            signature_offset = alg.hash_num_bytes.toULong()
            signature_size = alg.signature_num_bytes.toULong()

            public_key_offset = descriptors_size
            public_key_size = AuxBlob.encodePubKey(alg).size.toULong()

            public_key_metadata_size = ai.auxBlob!!.pubkeyMeta?.pkmd?.size?.toULong() ?: 0U
            public_key_metadata_offset = public_key_offset + public_key_size
            log.info("pkmd size: $public_key_metadata_size, pkmd offset : $public_key_metadata_offset")
        }.encode()

        //2 - auth blob
        var authBlob = byteArrayOf()
        if (ai.authBlob != null) {
            authBlob = AuthBlob.createBlob(headerBlob, auxBlob, alg.name)
        } else {
            log.info("No auth blob")
        }

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
        const val AVB_VERSION_MAJOR = 1U
        const val AVB_VERSION_MINOR = 1U
        const val AVB_VERSION_SUB = 0

        fun getJsonFileName(image_file: String): String {
            val fileName = File(image_file).name
            val jsonFile = "$fileName.avb.json"
            return UnifiedConfig.workDir + jsonFile
        }
    }
}
