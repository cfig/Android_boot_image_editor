package avb.blob

import avb.alg.Algorithm
import avb.desc.*
import cfig.Helper
import cfig.io.Struct3
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Paths

@OptIn(ExperimentalUnsignedTypes::class)
@JsonIgnoreProperties("descriptorSize")
class AuxBlob(
        var pubkey: PubKeyInfo? = null,
        var pubkeyMeta: PubKeyMetadataInfo? = null,
        var propertyDescriptor: MutableList<PropertyDescriptor> = mutableListOf(),
        var hashTreeDescriptor: MutableList<HashTreeDescriptor> = mutableListOf(),
        var hashDescriptors: MutableList<HashDescriptor> = mutableListOf(),
        var kernelCmdlineDescriptor: MutableList<KernelCmdlineDescriptor> = mutableListOf(),
        var chainPartitionDescriptor: MutableList<ChainPartitionDescriptor> = mutableListOf(),
        var unknownDescriptors: MutableList<UnknownDescriptor> = mutableListOf()) {

    val descriptorSize: Int
        get(): Int {
            return this.encodeDescriptors().size
        }

    data class PubKeyInfo(
            var offset: Long = 0L,
            var size: Long = 0L,
            var pubkey: ByteArray = byteArrayOf()
    )

    data class PubKeyMetadataInfo(
            var offset: Long = 0L,
            var size: Long = 0L,
            var pkmd: ByteArray = byteArrayOf()
    )

    private fun encodeDescriptors(): ByteArray {
        return mutableListOf<Descriptor>().let { descList ->
            arrayOf(this.propertyDescriptor,        //tag 0
                    this.hashTreeDescriptor,        //tag 1
                    this.hashDescriptors,           //tag 2
                    this.kernelCmdlineDescriptor,   //tag 3
                    this.chainPartitionDescriptor,  //tag 4
                    this.unknownDescriptors         //tag X
            ).forEach { typedList ->
                typedList.forEach { descList.add(it) }
            }
            var ret = byteArrayOf()
            descList.sortBy { it.sequence }
            descList.forEach { ret = Helper.join(ret, it.encode()) }
            ret
        }
    }

    //encoded_descriptors + encoded_key + pkmd_blob + (padding)
    fun encode(alg: Algorithm): ByteArray {
        //descriptors
        val encodedDesc = this.encodeDescriptors()
        //pubkey
        val encodedKey = encodePubKey(alg)
        if (this.pubkey != null) {
            if (encodedKey.contentEquals(this.pubkey!!.pubkey)) {
                log.info("Using the same key as original vbmeta")
            } else {
                log.warn("Using different key from original vbmeta")
            }
        } else {
            log.info("no pubkey in auxBlob")
        }
        //pkmd
        var encodedPkmd = byteArrayOf()
        if (this.pubkeyMeta != null) {
            encodedPkmd = this.pubkeyMeta!!.pkmd
            log.warn("adding pkmd [size=${this.pubkeyMeta!!.pkmd.size}]...")
        } else {
            log.info("no pubkey metadata in auxBlob")
        }

        val auxSize = Helper.round_to_multiple(
                (encodedDesc.size + encodedKey.size + encodedPkmd.size).toLong(),
                64)
        return Struct3("${auxSize}b").pack(Helper.join(encodedDesc, encodedKey, encodedPkmd))
    }

    companion object {
        fun encodePubKey(alg: Algorithm, key: ByteArray? = null): ByteArray {
            var encodedKey = byteArrayOf()
            var algKey: ByteArray? = key
            if (alg.public_key_num_bytes > 0) {
                if (key == null) {
                    algKey = Files.readAllBytes((Paths.get(alg.defaultKey)))
                }
                encodedKey = Helper.encodeRSAkey(algKey!!)
                log.info("encodePubKey(): size = ${alg.public_key_num_bytes}, algorithm key size: ${encodedKey.size}")
                assert(alg.public_key_num_bytes == encodedKey.size)
            } else {
                log.info("encodePubKey(): No key to encode for algorithm " + alg.name)
            }
            return encodedKey
        }

        private val log = LoggerFactory.getLogger(AuxBlob::class.java)
    }
}
