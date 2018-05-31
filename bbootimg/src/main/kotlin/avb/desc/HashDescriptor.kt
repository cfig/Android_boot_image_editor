package avb.desc

import cfig.Helper
import cfig.io.Struct
import org.junit.Assert
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

class HashDescriptor(var image_size: Long = 0L,
                     var hash_algorithm: ByteArray = byteArrayOf(),
                     var partition_name_len: Long = 0L,
                     var salt_len: Long = 0L,
                     var digest_len: Long = 0L,
                     var flags: Long = 0L,
                     var partition_name: String = "",
                     var salt: ByteArray = byteArrayOf(),
                     var digest: ByteArray = byteArrayOf()) : Descriptor(TAG, 0, 0) {
    constructor(data: InputStream, seq: Int = 0) : this() {
        val info = Struct(FORMAT_STRING).unpack(data)
        this.tag = info[0] as Long
        this.num_bytes_following = info[1] as Long
        this.image_size = info[2] as Long
        this.hash_algorithm = info[3] as ByteArray
        this.partition_name_len = info[4] as Long
        this.salt_len = info[5] as Long
        this.digest_len = info[6] as Long
        this.flags = info[7] as Long
        this.sequence = seq
        val expectedSize = Helper.round_to_multiple(SIZE - 16 + partition_name_len + salt_len + digest_len, 8)
        if (this.tag != TAG || expectedSize != this.num_bytes_following) {
            throw IllegalArgumentException("Given data does not look like a |hash| descriptor")
        }
        val payload = Struct("${this.partition_name_len}s${this.salt_len}b${this.digest_len}b").unpack(data)
        Assert.assertEquals(3, payload.size)
        this.partition_name = Helper.toCString(payload[0] as ByteArray)
        this.salt = payload[1] as ByteArray
        this.digest = payload[2] as ByteArray
    }

    override fun encode(): ByteArray {
        val payload_bytes_following = SIZE + this.partition_name.length + this.salt.size + this.digest.size - 16L
        this.num_bytes_following = Helper.round_to_multiple(payload_bytes_following, 8)
        val padding_size = num_bytes_following - payload_bytes_following
        val desc = Struct(FORMAT_STRING).pack(
                TAG,
                this.num_bytes_following,
                this.image_size,
                this.hash_algorithm,
                this.partition_name.length,
                this.salt.size,
                this.digest.size,
                this.flags,
                null)
        val padding = Struct("${padding_size}x").pack(null)
        return Helper.join(desc, partition_name.toByteArray(), this.salt, this.digest, padding)
    }

    fun verify(image_file: String) {
        val hasher = MessageDigest.getInstance(Helper.pyAlg2java(hash_algorithm.toString()))
        hasher.update(this.salt)
        hasher.update(File(image_file).readBytes())
        val digest = hasher.digest()
    }

    companion object {
        const val TAG = 2L
        private const val RESERVED = 60
        private const val SIZE = 72 + RESERVED
        private const val FORMAT_STRING = "!3Q32s4L${RESERVED}s"
    }

    override fun toString(): String {
        return "HashDescriptor(TAG=$TAG, image_size=$image_size, hash_algorithm=${Helper.toCString(hash_algorithm)}, flags=$flags, partition_name='$partition_name', salt=${Helper.toHexString(salt)}, digest=${Helper.toHexString(digest)})"
    }
}