package avb.desc

import cfig.Helper
import cfig.io.Struct3
import java.io.InputStream
import java.security.MessageDigest
import java.util.*

@OptIn(ExperimentalUnsignedTypes::class)
class ChainPartitionDescriptor(
        var rollback_index_location: UInt = 0U,
        var partition_name_len: UInt = 0U,
        var public_key_len: UInt = 0U,
        var partition_name: String = "",
        var pubkey: ByteArray = byteArrayOf(),
        var pubkey_sha1: String = ""
) : Descriptor(TAG, 0U, 0) {
    override fun encode(): ByteArray {
        this.partition_name_len = this.partition_name.length.toUInt()
        this.public_key_len = this.pubkey.size.toUInt()
        this.num_bytes_following = (SIZE.toUInt() + this.partition_name_len + this.public_key_len - 16U).toULong()
        val nbf_with_padding = Helper.round_to_multiple(this.num_bytes_following.toLong(), 8).toULong()
        val padding_size = nbf_with_padding - this.num_bytes_following
        val desc = Struct3(FORMAT_STRING + "${RESERVED}x").pack(
                TAG,
                nbf_with_padding.toULong(),
                this.rollback_index_location,
                this.partition_name.length.toUInt(),
                this.public_key_len,
                null)
        val padding = Struct3("${padding_size}x").pack(null)
        return Helper.join(desc, this.partition_name.toByteArray(), this.pubkey, padding)
    }

    companion object {
        const val TAG: ULong = 4U
        const val RESERVED = 64
        const val SIZE = 28L + RESERVED
        const val FORMAT_STRING = "!2Q3L"
    }

    constructor(data: InputStream, seq: Int = 0) : this() {
        if (SIZE - RESERVED != Struct3(FORMAT_STRING).calcSize().toLong()) {
            throw RuntimeException()
        }
        this.sequence = seq
        val info = Struct3(FORMAT_STRING + "${RESERVED}s").unpack(data)
        this.tag = info[0] as ULong
        this.num_bytes_following = info[1] as ULong
        this.rollback_index_location = info[2] as UInt
        this.partition_name_len = info[3] as UInt
        this.public_key_len = info[4] as UInt
        val expectedSize = Helper.round_to_multiple(
                SIZE.toUInt() - 16U + this.partition_name_len + this.public_key_len, 8U)
        if (this.tag != TAG || this.num_bytes_following != expectedSize.toULong()) {
            throw IllegalArgumentException("Given data does not look like a chain/delegation descriptor")
        }
        val info2 = Struct3("${this.partition_name_len}s${this.public_key_len}b").unpack(data)
        this.partition_name = info2[0] as String
        this.pubkey = info2[1] as ByteArray
        val md = MessageDigest.getInstance("SHA1")
        md.update(this.pubkey)
        this.pubkey_sha1 = Helper.toHexString(md.digest())
    }

    override fun toString(): String {
        return "ChainPartitionDescriptor(partition=${this.partition_name}, pubkey=${Arrays.toString(this.pubkey)}"
    }
}
