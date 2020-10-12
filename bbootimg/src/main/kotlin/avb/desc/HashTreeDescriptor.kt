package avb.desc

import avb.blob.Header
import cfig.helper.Helper
import cfig.io.Struct3
import java.io.InputStream
import java.util.*

@OptIn(ExperimentalUnsignedTypes::class)
class HashTreeDescriptor(
        var flags: Int = 0,
        var dm_verity_version: Int = 0,
        var image_size: Long = 0,
        var tree_offset: Long = 0,
        var tree_size: Long = 0,
        var data_block_size: Int = 0,
        var hash_block_size: Int = 0,
        var fec_num_roots: Int = 0,
        var fec_offset: Long = 0,
        var fec_size: Long = 0,
        var hash_algorithm: String = "",
        var partition_name: String = "",
        var salt: ByteArray = byteArrayOf(),
        var root_digest: ByteArray = byteArrayOf()) : Descriptor(TAG, 0, 0) {
    var flagsInterpretation: String = ""
        get() {
            var ret = ""
            if (this.flags and Header.HashTreeDescriptorFlags.AVB_HASHTREE_DESCRIPTOR_FLAGS_DO_NOT_USE_AB.inFlags == 1) {
                ret += "1:no-A/B system"
            } else {
                ret += "0:A/B system"
            }
            return ret
        }

    constructor(data: InputStream, seq: Int = 0) : this() {
        this.sequence = seq
        val info = Struct3(FORMAT_STRING).unpack(data)
        this.tag = (info[0] as ULong).toLong()
        this.num_bytes_following = (info[1] as ULong).toLong()
        this.dm_verity_version = (info[2] as UInt).toInt()
        this.image_size = (info[3] as ULong).toLong()
        this.tree_offset = (info[4] as ULong).toLong()
        this.tree_size = (info[5] as ULong).toLong()
        this.data_block_size = (info[6] as UInt).toInt()
        this.hash_block_size = (info[7] as UInt).toInt()
        this.fec_num_roots = (info[8] as UInt).toInt()
        this.fec_offset = (info[9] as ULong).toLong()
        this.fec_size = (info[10] as ULong).toLong()
        this.hash_algorithm = info[11] as String
        val partition_name_len = info[12] as UInt
        val salt_len = info[13] as UInt
        val root_digest_len = info[14] as UInt
        this.flags = (info[15] as UInt).toInt()
        val expectedSize = Helper.round_to_multiple(SIZE.toUInt() - 16U + partition_name_len + salt_len + root_digest_len, 8U)
        if (this.tag != TAG || this.num_bytes_following != expectedSize.toLong()) {
            throw IllegalArgumentException("Given data does not look like a hashtree descriptor")
        }

        val info2 = Struct3("${partition_name_len}s${salt_len}b${root_digest_len}b").unpack(data)
        this.partition_name = info2[0] as String
        this.salt = info2[1] as ByteArray
        this.root_digest = info2[2] as ByteArray
    }

    override fun encode(): ByteArray {
        this.num_bytes_following = SIZE + this.partition_name.length + this.salt.size + this.root_digest.size - 16
        val nbf_with_padding = Helper.round_to_multiple(this.num_bytes_following.toLong(), 8)
        val padding_size = nbf_with_padding - this.num_bytes_following.toLong()
        val desc = Struct3(FORMAT_STRING).pack(
                TAG,
                nbf_with_padding.toULong(),
                this.dm_verity_version,
                this.image_size,
                this.tree_offset,
                this.tree_size,
                this.data_block_size,
                this.hash_block_size,
                this.fec_num_roots,
                this.fec_offset,
                this.fec_size,
                this.hash_algorithm,
                this.partition_name.length,
                this.salt.size,
                this.root_digest.size,
                this.flags,
                null)
        val padding = Struct3("${padding_size}x").pack(null)
        return Helper.join(desc, this.partition_name.toByteArray(), this.salt, this.root_digest, padding)
    }

    override fun toString(): String {
        return "HashTreeDescriptor(dm_verity_version=$dm_verity_version, image_size=$image_size, tree_offset=$tree_offset, tree_size=$tree_size, data_block_size=$data_block_size, hash_block_size=$hash_block_size, fec_num_roots=$fec_num_roots, fec_offset=$fec_offset, fec_size=$fec_size, hash_algorithm='$hash_algorithm', partition_name='$partition_name', salt=${Arrays.toString(salt)}, root_digest=${Arrays.toString(root_digest)}, flags=$flags)"
    }

    companion object {
        const val TAG: Long = 1L
        private const val RESERVED = 60L
        private const val SIZE = 120 + RESERVED
        private const val FORMAT_STRING = "!2QL3Q3L2Q32s4L${RESERVED}x"
    }
}
