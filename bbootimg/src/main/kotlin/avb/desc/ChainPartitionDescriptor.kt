package avb.desc

import cfig.Helper
import cfig.io.Struct
import java.io.InputStream
import java.security.MessageDigest
import java.util.*

class ChainPartitionDescriptor(
        var rollback_index_location: Long = 0,
        var partition_name_len: Long = 0,
        var public_key_len: Long = 0,
        var partition_name: String = "",
        var pubkey: ByteArray = byteArrayOf(),
        var pubkey_sha1: String = ""
) : Descriptor(TAG, 0, 0) {
    override fun encode(): ByteArray {
        this.partition_name_len = this.partition_name.length.toLong()
        this.public_key_len = this.pubkey.size.toLong()
        this.num_bytes_following = SIZE + this.partition_name_len + this.public_key_len - 16
        val nbf_with_padding = Helper.round_to_multiple(this.num_bytes_following, 8)
        val padding_size = nbf_with_padding - this.num_bytes_following
        val desc = Struct(FORMAT_STRING + "${RESERVED}s").pack(
                TAG,
                nbf_with_padding,
                this.rollback_index_location,
                this.partition_name.length,
                this.public_key_len,
                null)
        val padding = Struct("${padding_size}s").pack(null)
        return Helper.join(desc, this.partition_name.toByteArray(), this.pubkey, padding)
    }

    companion object {
        const val TAG = 4L
        const val RESERVED = 64
        const val SIZE = 28L + RESERVED
        const val FORMAT_STRING = "!2Q3L"
    }

    constructor(data: InputStream, seq: Int = 0) : this() {
        if (SIZE - RESERVED != Struct(FORMAT_STRING).calcSize().toLong()) {
            throw RuntimeException()
        }
        this.sequence = seq
        val info = Struct(FORMAT_STRING + "${RESERVED}s").unpack(data)
        this.tag = info[0] as Long
        this.num_bytes_following = info[1] as Long
        this.rollback_index_location = info[2] as Long
        this.partition_name_len = info[3] as Long
        this.public_key_len = info[4] as Long
        val expectedSize = Helper.round_to_multiple(
                SIZE - 16 + this.partition_name_len + this.public_key_len, 8)
        if (this.tag != TAG || this.num_bytes_following != expectedSize) {
            throw IllegalArgumentException("Given data does not look like a chain/delegation descriptor")
        }
        val info2 = Struct("${this.partition_name_len}s${this.public_key_len}s").unpack(data)
        this.partition_name = Helper.toCString(info2[0] as ByteArray)
        this.pubkey = info2[1] as ByteArray
        val md = MessageDigest.getInstance("SHA1")
        md.update(this.pubkey)
        this.pubkey_sha1 = Helper.toHexString(md.digest())
    }

    override fun toString(): String {
        return "ChainPartitionDescriptor(partition=${this.partition_name}, pubkey=${Arrays.toString(this.pubkey)}"
    }
}