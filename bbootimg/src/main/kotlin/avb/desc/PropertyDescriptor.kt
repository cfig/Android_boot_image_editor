package avb.desc

import cfig.Helper
import cfig.io.Struct3
import java.io.InputStream

@ExperimentalUnsignedTypes
class PropertyDescriptor(
        var key: String = "",
        var value: String = "") : Descriptor(TAG, 0U, 0) {
    override fun encode(): ByteArray {
        if (SIZE != Struct3(FORMAT_STRING).calcSize()!!.toUInt()) {
            throw RuntimeException()
        }
        this.num_bytes_following = (SIZE + this.key.length.toUInt() + this.value.length.toUInt() + 2U - 16U).toULong()
        val nbfWithPadding = Helper.round_to_multiple(this.num_bytes_following.toLong(), 8).toULong()
        val paddingSize = nbfWithPadding - num_bytes_following
        val padding = Struct3("${paddingSize}x").pack(0)
        val desc = Struct3(FORMAT_STRING).pack(
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
        val info = Struct3(FORMAT_STRING).unpack(data)
        this.tag = info[0] as ULong
        this.num_bytes_following = info[1] as ULong
        val keySize = (info[2] as ULong).toUInt()
        val valueSize = (info[3] as ULong).toUInt()
        val expectedSize = Helper.round_to_multiple(SIZE - 16U + keySize + 1U + valueSize + 1U, 8U)
        if (this.tag != TAG || expectedSize.toULong() != this.num_bytes_following) {
            throw IllegalArgumentException("Given data does not look like a |property| descriptor")
        }
        this.sequence = seq

        val info2 = Struct3("${keySize}sx${valueSize}s").unpack(data)
        this.key = info2[0] as String
        this.value = info2[2] as String
    }

    companion object {
        const val TAG: ULong = 0U
        const val SIZE = 32U
        const val FORMAT_STRING = "!4Q"
    }
}