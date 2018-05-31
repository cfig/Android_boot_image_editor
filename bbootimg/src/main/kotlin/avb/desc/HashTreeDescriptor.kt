package avb.desc

import cfig.Helper
import cfig.io.Struct
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.util.*

class HashTreeDescriptor(
        var dm_verity_version: Long = 0L,
        var image_size: Long = 0L,
        var tree_offset: Long = 0L,
        var tree_size: Long = 0L,
        var data_block_size: Long = 0L,
        var hash_block_size: Long = 0L,
        var fec_num_roots: Long = 0L,
        var fec_offset: Long = 0L,
        var fec_size: Long = 0L,
        var hash_algorithm: String = "",
        var partition_name: String = "",
        var salt: ByteArray = byteArrayOf(),
        var root_digest: ByteArray = byteArrayOf(),
        var flags: Long = 0L) : Descriptor(TAG, 0, 0) {
    constructor(data: InputStream, seq: Int = 0) : this() {
        this.sequence = seq
        val info = Struct(FORMAT_STRING).unpack(data)
        this.tag = info[0] as Long
        this.num_bytes_following = info[1] as Long
        this.dm_verity_version = info[2] as Long
        this.image_size = info[3] as Long
        this.tree_offset = info[4] as Long
        this.tree_size = info[5] as Long
        this.data_block_size = info[6] as Long
        this.hash_block_size = info[7] as Long
        this.fec_num_roots = info[8] as Long
        this.fec_offset = info[9] as Long
        this.fec_size = info[10] as Long
        this.hash_algorithm = Helper.toCString(info[11] as ByteArray)
        val partition_name_len = info[12] as Long
        val salt_len = info[13] as Long
        val root_digest_len = info[14] as Long
        this.flags = info[15] as Long
        val expectedSize = Helper.round_to_multiple(SIZE - 16 + partition_name_len + salt_len + root_digest_len, 8)
        if (this.tag != TAG || this.num_bytes_following != expectedSize) {
            throw IllegalArgumentException("Given data does not look like a hashtree descriptor")
        }

        val info2 = Struct("${partition_name_len}s${salt_len}s${root_digest_len}s").unpack(data)
        this.partition_name = Helper.toCString(info2[0] as ByteArray)
        this.salt = info2[1] as ByteArray
        this.root_digest = info2[2] as ByteArray
    }

    override fun encode(): ByteArray {
        this.num_bytes_following = SIZE + this.partition_name.length + this.salt.size + this.root_digest.size - 16
        val nbf_with_padding = Helper.round_to_multiple(this.num_bytes_following, 8)
        val padding_size = nbf_with_padding - this.num_bytes_following
        val desc = Struct(FORMAT_STRING).pack(
                TAG,
                nbf_with_padding,
                this.dm_verity_version,
                this.image_size,
                this.tree_offset,
                this.tree_size,
                this.data_block_size,
                this.hash_block_size,
                this.fec_num_roots,
                this.fec_offset,
                this.fec_size,
                this.hash_algorithm.toByteArray(),
                this.partition_name.length,
                this.salt.size,
                this.root_digest.size,
                this.flags,
                null)
        val padding = Struct("${padding_size}x").pack(null)
        return Helper.join(desc, this.partition_name.toByteArray(), this.salt, this.root_digest, padding)
    }

    override fun toString(): String {
        return "HashTreeDescriptor(dm_verity_version=$dm_verity_version, image_size=$image_size, tree_offset=$tree_offset, tree_size=$tree_size, data_block_size=$data_block_size, hash_block_size=$hash_block_size, fec_num_roots=$fec_num_roots, fec_offset=$fec_offset, fec_size=$fec_size, hash_algorithm='$hash_algorithm', partition_name='$partition_name', salt=${Arrays.toString(salt)}, root_digest=${Arrays.toString(root_digest)}, flags=$flags)"
    }

    companion object {
        const val TAG = 1L
        private const val RESERVED = 60L
        private const val SIZE = 120 + RESERVED
        private const val FORMAT_STRING = "!2QL3Q3L2Q32s4L${RESERVED}s"
        private val log = LoggerFactory.getLogger(HashTreeDescriptor::class.java)
    }
}