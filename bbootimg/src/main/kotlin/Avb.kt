package cfig

import avb.*
import avb.alg.Algorithms
import avb.desc.*
import cfig.io.Struct
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.commons.codec.binary.Hex
import org.slf4j.LoggerFactory
import java.io.*
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

class Avb {
    val MAX_VBMETA_SIZE = 64 * 1024
    val MAX_FOOTER_SIZE = 4096
    val BLOCK_SIZE = 4096

    private var required_libavb_version_minor = 0

    fun add_hash_footer(image_file: String,
                        partition_size: Long,
                        use_persistent_digest: Boolean,
                        do_not_use_ab: Boolean,
                        salt: String,
                        hash_algorithm: String,
                        partition_name: String,
                        rollback_index: Long,
                        common_algorithm: String,
                        inReleaseString: String?) {
        var original_image_size = 0L
        //required libavb version
        if (use_persistent_digest || do_not_use_ab) {
            required_libavb_version_minor = 1
        }
        log.info("Required_libavb_version: 1.$required_libavb_version_minor")

        // SIZE + metadata (footer + vbmeta struct)
        val max_metadata_size = MAX_VBMETA_SIZE + MAX_FOOTER_SIZE
        if (partition_size < max_metadata_size) {
            throw IllegalArgumentException("Parition SIZE of $partition_size is too small. " +
                    "Needs to be at least $max_metadata_size")
        }
        val max_image_size = partition_size - max_metadata_size
        log.info("max_image_size: $max_image_size")

        if (partition_size % 4096L != 0L) {
            throw IllegalArgumentException("Partition SIZE of $partition_size is not a multiple of the image block SIZE 4096")
        }

        val fis = FileInputStream(image_file)
        fis.skip(File(image_file).length() - 64)
        try {
            val footer = Footer(fis)
            original_image_size = footer.originalImageSize
            FileOutputStream(File(image_file), true).channel.use {
                log.info("truncate $image_file to its original SIZE ${footer.originalImageSize}")
                it.truncate(footer.originalImageSize)
            }
        } catch (e: IllegalArgumentException) {
            log.info("original image doesn't have footer")
            original_image_size = File(image_file).length()
        }

        val saltByteArray = Helper.fromHexString(salt)
        val digest = MessageDigest.getInstance(Helper.pyAlg2java(hash_algorithm)).apply {
            update(saltByteArray)
            update(File(image_file).readBytes())
        }.digest()
        log.info("Digest: " + Helper.toHexString(digest))

        val hd = HashDescriptor()
        hd.image_size = File(image_file).length()
        hd.hash_algorithm = hash_algorithm.toByteArray()
        hd.partition_name = partition_name
        hd.salt = saltByteArray
        hd.flags = 0
        if (do_not_use_ab) hd.flags = hd.flags or 1
        if (!use_persistent_digest) hd.digest = digest
        log.info("encoded hash descriptor:" + Hex.encodeHexString(hd.encode()))
        val vbmeta_blob = generateVbMetaBlob(common_algorithm,
                null,
                arrayOf(hd as Descriptor),
                null,
                rollback_index,
                0,
                null,
                null,
                0,
                inReleaseString)
        log.debug("vbmeta_blob: " + Helper.toHexString(vbmeta_blob))

        if (hd.image_size % BLOCK_SIZE != 0L) {
            val padding_needed = BLOCK_SIZE - (hd.image_size % BLOCK_SIZE)
            FileOutputStream(image_file, true).use { fos ->
                fos.write(ByteArray(padding_needed.toInt()))
            }
            log.info("$image_file padded: ${hd.image_size} -> ${File(image_file).length()}")
        } else {
            log.info("$image_file doesn't need padding")
        }
        val vbmeta_offset = hd.image_size
        val padding_needed = Helper.round_to_multiple(vbmeta_blob.size.toLong(), BLOCK_SIZE) - vbmeta_blob.size
        val vbmeta_blob_with_padding = Helper.join(vbmeta_blob, Struct("${padding_needed}x").pack(null))
        FileOutputStream(image_file, true).use { fos ->
            fos.write(vbmeta_blob_with_padding)
        }
        val vbmeta_end_offset = vbmeta_offset + vbmeta_blob_with_padding.size
        FileOutputStream(image_file, true).use { fos ->
            fos.write(Struct("${partition_size - vbmeta_end_offset - 1 * BLOCK_SIZE}x").pack(null))
        }

        val footer = Footer()
        footer.originalImageSize = original_image_size
        footer.vbMetaOffset = vbmeta_offset
        footer.vbMetaSize = vbmeta_blob.size.toLong()
        val footerBob = footer.encode()
        val footerBlobWithPadding = Helper.join(
                Struct("${BLOCK_SIZE - Footer.SIZE}x").pack(null), footerBob)
        log.info("footer:" + Helper.toHexString(footerBob))
        log.info(footer.toString())
        FileOutputStream(image_file, true).use { fos ->
            fos.write(footerBlobWithPadding)
        }
    }

    fun generateVbMetaBlob(algorithm_name: String,
                           public_key_metadata_path: String?,
                           descriptors: Array<Descriptor>,
                           chain_partitions: String?,
                           inRollbackIndex: Long,
                           inFlags: Long,
                           props: String?,
                           kernel_cmdlines: String?,
                           required_libavb_version_minor: Int,
                           inReleaseString: String?): ByteArray {
        //encoded descriptors
        var encodedDesc: ByteArray = byteArrayOf()
        descriptors.forEach { encodedDesc = Helper.join(encodedDesc, it.encode()) }
        //algorithm
        val alg = Algorithms.get(algorithm_name)!!
        //encoded pubkey
        val encodedKey = Blob.encodePubKey(alg)

        //3 - whole aux blob
        val auxBlob = Blob.getAuxDataBlob(encodedDesc, encodedKey)

        //1 - whole header blob
        val headerBlob = Header().apply {
            bump_required_libavb_version_minor(required_libavb_version_minor)
            auxiliary_data_block_size = auxBlob.size.toLong()

            authentication_data_block_size = Helper.round_to_multiple(
                    (alg.hash_num_bytes + alg.signature_num_bytes).toLong(), 64)

            algorithm_type = alg.algorithm_type.toLong()

            hash_offset = 0
            hash_size = alg.hash_num_bytes.toLong()

            signature_offset = alg.hash_num_bytes.toLong()
            signature_size = alg.signature_num_bytes.toLong()

            descriptors_offset = 0
            descriptors_size = encodedDesc.size.toLong()

            public_key_offset = descriptors_size
            public_key_size = encodedKey.size.toLong()

            //TODO: support pubkey metadata
            public_key_metadata_size = 0
            public_key_metadata_offset = public_key_offset + public_key_size

            rollback_index = inRollbackIndex
            flags = inFlags
            if (inReleaseString != null) {
                log.info("Using preset release string: $inReleaseString")
                this.release_string = inReleaseString
            }
        }.encode()

        //2 - auth blob
        var authBlob = Blob.getAuthBlob(headerBlob, auxBlob, algorithm_name)

        return Helper.join(headerBlob, authBlob, auxBlob)
    }

    fun parseVbMeta(image_file: String): AVBInfo {
        log.info("parsing $image_file ...")
        val jsonFile = getJsonFileName(image_file)
        var footer: Footer? = null
        var vbMetaOffset = 0L
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

        var vbMetaHeader = Header()
        FileInputStream(image_file).use { fis ->
            fis.skip(vbMetaOffset)
            vbMetaHeader = Header(fis)
        }
        log.info(vbMetaHeader.toString())
        log.debug(ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(vbMetaHeader))

        val authBlockOffset = vbMetaOffset + Header.SIZE
        val auxBlockOffset = authBlockOffset + vbMetaHeader.authentication_data_block_size
        val descStartOffset = auxBlockOffset + vbMetaHeader.descriptors_offset

        val ai = AVBInfo()
        ai.footer = footer
        ai.auxBlob = AVBInfo.AuxBlob()
        ai.header = vbMetaHeader
        if (vbMetaHeader.public_key_size > 0L) {
            ai.auxBlob!!.pubkey = AVBInfo.AuxBlob.PubKeyInfo()
            ai.auxBlob!!.pubkey!!.offset = vbMetaHeader.public_key_offset
            ai.auxBlob!!.pubkey!!.size = vbMetaHeader.public_key_size
        }
        if (vbMetaHeader.public_key_metadata_size > 0L) {
            ai.auxBlob!!.pubkeyMeta = AVBInfo.AuxBlob.PubKeyMetadataInfo()
            ai.auxBlob!!.pubkeyMeta!!.offset = vbMetaHeader.public_key_metadata_offset
            ai.auxBlob!!.pubkeyMeta!!.size = vbMetaHeader.public_key_metadata_size
        }

        var descriptors: List<Any> = mutableListOf()
        if (vbMetaHeader.descriptors_size > 0) {
            FileInputStream(image_file).use { fis ->
                fis.skip(descStartOffset)
                descriptors = UnknownDescriptor.parseDescriptors2(fis, vbMetaHeader.descriptors_size)
            }

            descriptors.forEach {
                log.debug(it.toString())
            }
        }

        if (vbMetaHeader.public_key_size > 0) {
            FileInputStream(image_file).use { fis ->
                fis.skip(auxBlockOffset)
                fis.skip(vbMetaHeader.public_key_offset)
                ai.auxBlob!!.pubkey!!.pubkey = ByteArray(vbMetaHeader.public_key_size.toInt())
                fis.read(ai.auxBlob!!.pubkey!!.pubkey)
                log.debug("Parsed Pub Key: " + Hex.encodeHexString(ai.auxBlob!!.pubkey!!.pubkey))
            }
        }

        if (vbMetaHeader.public_key_metadata_size > 0) {
            FileInputStream(image_file).use { fis ->
                fis.skip(vbMetaOffset)
                fis.skip(Header.SIZE.toLong())
                fis.skip(vbMetaHeader.public_key_metadata_offset)
                val ba = ByteArray(vbMetaHeader.public_key_metadata_size.toInt())
                fis.read(ba)
                log.debug("Parsed Pub Key Metadata: " + Hex.encodeHexString(ba))
            }
        }

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

                ai.authBlob = AVBInfo.AuthBlob()
                ai.authBlob!!.offset = authBlockOffset
                ai.authBlob!!.size = vbMetaHeader.authentication_data_block_size
                ai.authBlob!!.hash = Hex.encodeHexString(ba)
                ai.authBlob!!.signature = Hex.encodeHexString(bb)
            }
        }

        descriptors.forEach {
            when (it) {
                is HashDescriptor -> {
                    ai.auxBlob!!.hashDescriptors.add(it)
                }
                is KernelCmdlineDescriptor -> {
                    ai.auxBlob!!.kernelCmdlineDescriptor.add(it)
                }
                is HashTreeDescriptor -> {
                    ai.auxBlob!!.hashTreeDescriptor.add(it)
                }
                is UnknownDescriptor -> {
                    ai.auxBlob!!.unknownDescriptors.add(it)
                }
                else -> {
                    throw IllegalArgumentException("invalid descriptor: $it")
                }
            }
        }
        val aiStr = ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(ai)
        log.debug(aiStr)
        ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(File(jsonFile), ai)
        log.info("vbmeta info written to $jsonFile")

        return ai
    }

    fun packVbMeta(info: AVBInfo? = null): ByteArray {
        val ai = info ?: ObjectMapper().readValue(File(getJsonFileName("vbmeta.img")), AVBInfo::class.java)
        val alg = Algorithms.get(ai.header!!.algorithm_type.toInt())!!
        val encodedDesc = ai.auxBlob!!.encodeDescriptors()
        //encoded pubkey
        val encodedKey = Blob.encodePubKey(alg, Files.readAllBytes(Paths.get(alg.defaultKey)))

        //3 - whole aux blob
        var auxBlob = byteArrayOf()
        if (ai.header!!.auxiliary_data_block_size > 0) {
            if (encodedKey.contentEquals(ai.auxBlob!!.pubkey!!.pubkey)) {
                log.info("Using the same key as original vbmeta")
            } else {
                log.warn("Using different key from original vbmeta")
            }
            auxBlob = Blob.getAuxDataBlob(encodedDesc, encodedKey)
        } else {
            log.info("No aux blob")
        }

        //1 - whole header blob
        val headerBlob = ai.header!!.apply {
            auxiliary_data_block_size = auxBlob.size.toLong()
            authentication_data_block_size = Helper.round_to_multiple(
                    (alg.hash_num_bytes + alg.signature_num_bytes).toLong(), 64)

            descriptors_offset = 0
            descriptors_size = encodedDesc.size.toLong()

            hash_offset = 0
            hash_size = alg.hash_num_bytes.toLong()

            signature_offset = alg.hash_num_bytes.toLong()
            signature_size = alg.signature_num_bytes.toLong()

            public_key_offset = descriptors_size
            public_key_size = encodedKey.size.toLong()

            //TODO: support pubkey metadata
            public_key_metadata_size = 0
            public_key_metadata_offset = public_key_offset + public_key_size
        }.encode()

        //2 - auth blob
        var authBlob = byteArrayOf()
        if (ai.authBlob != null) {
            authBlob = Blob.getAuthBlob(headerBlob, auxBlob, alg.name)
        } else {
            log.info("No auth blob")
        }

        return Helper.join(headerBlob, authBlob, auxBlob)
    }

    fun packVbMetaWithPadding(info: AVBInfo? = null) {
        val rawBlob = packVbMeta(info)
        val paddingSize = Helper.round_to_multiple(rawBlob.size.toLong(), BLOCK_SIZE) - rawBlob.size
        val paddedBlob = Helper.join(rawBlob, Struct("${paddingSize}x").pack(null))
        log.info("raw vbmeta size ${rawBlob.size}, padding size $paddingSize, total blob size ${paddedBlob.size}")
        log.info("Writing padded vbmeta to file: vbmeta.img.signed")
        Files.write(Paths.get("vbmeta.img.signed"), paddedBlob, StandardOpenOption.CREATE)
    }

    companion object {
        private val log = LoggerFactory.getLogger(Avb::class.java)
        val AVB_VERSION_MAJOR = 1
        val AVB_VERSION_MINOR = 1
        val AVB_VERSION_SUB = 0

        fun getJsonFileName(image_file: String): String {
            val fileName = File(image_file).name
            val jsonFile = "$fileName.avb.json"
            return UnifiedConfig.workDir + jsonFile
        }
    }
}
