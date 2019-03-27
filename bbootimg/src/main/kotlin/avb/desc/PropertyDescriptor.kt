package avb.desc

import cfig.Helper
import cfig.io.Struct
import java.io.InputStream

class PropertyDescriptor(
        private var key: String = "",
        private var value: String = "") : Descriptor(TAG, 0, 0) {
    override fun encode(): ByteArray {
        if (SIZE != Struct(FORMAT_STRING).calcSize()) {
            throw RuntimeException()
        }
        this.num_bytes_following = SIZE + this.key.length + this.value.length + 2 - 16L
        val nbfWithPadding = Helper.round_to_multiple(this.num_bytes_following, 8)
        val paddingSize = nbfWithPadding - num_bytes_following
        val padding = Struct("${paddingSize}x").pack(0)
        val desc = Struct(FORMAT_STRING).pack(
                TAG,
                nbfWithPadding,
                this.key.length,
                this.value.length)
        return Helper.join(desc,
                this.key.toByteArray(), ByteArray(1),
                this.value.toByteArray(), ByteArray(1),
                padding)
    }

    constructor(data: InputStream, seq: Int = 0) : this() {
        val info = Struct(FORMAT_STRING).unpack(data)
        this.tag = info[0] as Long
        this.num_bytes_following = info[1] as Long
        val keySize = info[2] as Long
        val valueSize = info[3] as Long
        val expectedSize = Helper.round_to_multiple(SIZE - 16 + keySize + 1 + valueSize + 1, 8)
        if (this.tag != TAG || expectedSize != this.num_bytes_following) {
            throw IllegalArgumentException("Given data does not look like a |property| descriptor")
        }
        this.sequence = seq

        val info2 = Struct("${keySize}sx${valueSize}s").unpack(data)
        this.key = String(info2[0] as ByteArray)
        this.value = String(info2[2] as ByteArray)
    }

    companion object {
        const val TAG = 0L
        const val SIZE = 32
        const val FORMAT_STRING = "!4Q"
    }
}