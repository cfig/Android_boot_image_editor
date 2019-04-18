package avb

import avb.alg.Algorithm
import avb.desc.*
import cfig.Helper
import cfig.io.Struct3
import org.junit.Assert
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Paths

data class AuxBlob(
        var pubkey: PubKeyInfo? = null,
        var pubkeyMeta: PubKeyMetadataInfo? = null,
        var propertyDescriptor: MutableList<PropertyDescriptor> = mutableListOf(),
        var hashTreeDescriptor: MutableList<HashTreeDescriptor> = mutableListOf(),
        var hashDescriptors: MutableList<HashDescriptor> = mutableListOf(),
        var kernelCmdlineDescriptor: MutableList<KernelCmdlineDescriptor> = mutableListOf(),
        var chainPartitionDescriptor: MutableList<ChainPartitionDescriptor> = mutableListOf(),
        var unknownDescriptors: MutableList<UnknownDescriptor> = mutableListOf()
) {
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

    fun encodeDescriptors(): ByteArray {
        var ret = byteArrayOf()
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
            descList.sortBy { it.sequence }
            descList.forEach { ret = Helper.join(ret, it.encode()) }
            ret
        }
    }

    //encoded_descriptors + encoded_key + pkmd_blob + (padding)
    fun encode(): ByteArray {
        val encodedDesc = this.encodeDescriptors()
        var sumOfSize = encodedDesc.size
        this.pubkey?.let { sumOfSize += it.pubkey.size }
        this.pubkeyMeta?.let { sumOfSize += it.pkmd.size }
        val auxSize = Helper.round_to_multiple(sumOfSize.toLong(), 64)
        return Struct3("${auxSize}b").pack(
                Helper.joinWithNulls(encodedDesc, this.pubkey?.pubkey, this.pubkeyMeta?.pkmd))
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
                Assert.assertEquals(alg.public_key_num_bytes, encodedKey.size)
            } else {
                log.info("encodePubKey(): No key to encode for algorithm " + alg.name)
            }
            return encodedKey
        }

        private val log = LoggerFactory.getLogger(AuxBlob::class.java)
    }
}
