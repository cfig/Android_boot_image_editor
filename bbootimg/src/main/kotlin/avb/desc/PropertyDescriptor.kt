package avb.desc

import cfig.Helper
import cfig.io.Struct

class PropertyDescriptor(
        var key: String = "",
        var value: String = "") : Descriptor(TAG, 0, 0) {
    override fun encode(): ByteArray {
        this.num_bytes_following = SIZE + this.key.length + this.value.length + 2 - 16
        val nbf_with_padding = Helper.round_to_multiple(this.num_bytes_following, 8)
        val padding_size = nbf_with_padding - num_bytes_following
        val padding = Struct("${padding_size}x").pack(0)
        val desc = Struct(FORMAT_STRING).pack(
                TAG,
                nbf_with_padding,
                this.key.length,
                this.value.length)
        return Helper.join(desc,
                this.key.toByteArray(), ByteArray(1),
                this.value.toByteArray(), ByteArray(1),
                padding)
    }

    companion object {
        val TAG = 0L
        val SIZE = 32L
        val FORMAT_STRING = "!4Q"
    }
}