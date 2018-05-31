package avb.desc

import cfig.Helper
import cfig.io.Struct
import org.junit.Assert
import java.io.InputStream

class KernelCmdlineDescriptor(
        var flags: Long = 0,
        var cmdlineLength: Long = 0,
        var cmdline: String = "") : Descriptor(TAG, 0, 0) {
    @Throws(IllegalArgumentException::class)
    constructor(data: InputStream, seq: Int = 0) : this() {
        val info = Struct(FORMAT_STRING).unpack(data)
        this.tag = info[0] as Long
        this.num_bytes_following = info[1] as Long
        this.flags = info[2] as Long
        this.cmdlineLength = info[3] as Long
        this.sequence = seq
        val expectedSize = Helper.round_to_multiple(SIZE - 16 + this.cmdlineLength, 8)
        if ((this.tag != TAG) || (this.num_bytes_following != expectedSize)) {
            throw IllegalArgumentException("Given data does not look like a kernel cmdline descriptor")
        }
        this.cmdline = Helper.toCString(Struct("${this.cmdlineLength}s").unpack(data)[0] as ByteArray)
    }

    override fun encode(): ByteArray {
        val num_bytes_following = SIZE - 16 + cmdline.toByteArray().size
        val nbf_with_padding = Helper.round_to_multiple(num_bytes_following.toLong(), 8)
        val padding_size = nbf_with_padding - num_bytes_following
        val desc = Struct(FORMAT_STRING).pack(
                TAG,
                nbf_with_padding,
                this.flags,
                cmdline.toByteArray().size)
        val padding = Struct("${padding_size}x").pack(null)
        return Helper.join(desc, cmdline.toByteArray(), padding)
    }

    companion object {
        const val TAG = 3L
        const val SIZE = 24
        const val FORMAT_STRING = "!2Q2L" //# tag, num_bytes_following (descriptor header), flags, cmdline length (bytes)
        const val flagHashTreeEnabled = 1
        const val flagHashTreeDisabled = 2

        init {
            Assert.assertEquals(SIZE, Struct(FORMAT_STRING).calcsize())
        }
    }
}