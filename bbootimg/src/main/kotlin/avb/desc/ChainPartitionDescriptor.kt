package avb.desc

import cfig.helper.Helper
import cfig.io.Struct3
import java.io.InputStream
import java.security.MessageDigest
import java.util.*

@OptIn(ExperimentalUnsignedTypes::class)
class ChainPartitionDescriptor(
        var rollback_index_location: Int = 0,
        var partition_name_len: Int = 0,
        var public_key_len: Int = 0,
        var partition_name: String = "",
        var pubkey: ByteArray = byteArrayOf(),
        var pubkey_sha1: String = ""
) : Descriptor(TAG, 0, 0) {
    override fun encode(): ByteArray {
        this.partition_name_len = this.partition_name.length
        this.public_key_len = this.pubkey.size
        this.num_bytes_following = SIZE + this.partition_name_len + this.public_key_len - 16
        val nbf_with_padding = Helper.round_to_multiple(this.num_bytes_following, 8).toULong()
        val padding_size = nbf_with_padding - this.num_bytes_following.toUInt()
        val desc = Struct3(FORMAT_STRING + "${RESERVED}x").pack(
                TAG,
                nbf_with_padding,
                this.rollback_index_location,
                this.partition_name.length.toUInt(),
                this.public_key_len,
                null)
        val padding = Struct3("${padding_size}x").pack(null)
        return Helper.join(desc, this.partition_name.toByteArray(), this.pubkey, padding)
    }

    companion object {
        const val TAG: Long = 4L
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
        this.tag = (info[0] as ULong).toLong()
        this.num_bytes_following = (info[1] as ULong).toLong()
        this.rollback_index_location = (info[2] as UInt).toInt()
        this.partition_name_len = (info[3] as UInt).toInt()
        this.public_key_len = (info[4] as UInt).toInt()
        val expectedSize = Helper.round_to_multiple(
                SIZE - 16 + this.partition_name_len + this.public_key_len, 8)
        if (this.tag != TAG || this.num_bytes_following != expectedSize.toLong()) {
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
