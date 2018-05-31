package avb

import cfig.Avb
import cfig.Helper
import cfig.io.Struct
import org.junit.Assert
import java.io.InputStream

data class Header(
        var required_libavb_version_major: Int = Avb.AVB_VERSION_MAJOR,
        var required_libavb_version_minor: Int = 0,
        var authentication_data_block_size: Long = 0L,
        var auxiliary_data_block_size: Long = 0L,
        var algorithm_type: Long = 0L,
        var hash_offset: Long = 0L,
        var hash_size: Long = 0L,
        var signature_offset: Long = 0L,
        var signature_size: Long = 0L,
        var public_key_offset: Long = 0L,
        var public_key_size: Long = 0L,
        var public_key_metadata_offset: Long = 0L,
        var public_key_metadata_size: Long = 0L,
        var descriptors_offset: Long = 0L,
        var descriptors_size: Long = 0L,
        var rollback_index: Long = 0L,
        var flags: Long = 0,
        var release_string: String = "avbtool ${Avb.AVB_VERSION_MAJOR}.${Avb.AVB_VERSION_MINOR}.${Avb.AVB_VERSION_SUB}") {
    fun bump_required_libavb_version_minor(minor: Int) {
        this.required_libavb_version_minor = maxOf(required_libavb_version_minor, minor)
    }

    @Throws(IllegalArgumentException::class)
    constructor(iS: InputStream) : this() {
        val info = Struct(FORMAT_STRING).unpack(iS)
        Assert.assertEquals(22, info.size)
        if (!(info[0] as ByteArray).contentEquals(magic.toByteArray())) {
            throw IllegalArgumentException("stream doesn't look like valid VBMeta Header")
        }
        this.required_libavb_version_major = (info[1] as Long).toInt()
        this.required_libavb_version_minor = (info[2] as Long).toInt()
        this.authentication_data_block_size = info[3] as Long
        this.auxiliary_data_block_size = info[4] as Long
        this.algorithm_type = info[5] as Long
        this.hash_offset = info[6] as Long
        this.hash_size = info[7] as Long
        this.signature_offset = info[8] as Long
        this.signature_size = info[9] as Long
        this.public_key_offset = info[10] as Long
        this.public_key_size = info[11] as Long
        this.public_key_metadata_offset = info[12] as Long
        this.public_key_metadata_size = info[13] as Long
        this.descriptors_offset = info[14] as Long
        this.descriptors_size = info[15] as Long
        this.rollback_index = info[16] as Long
        this.flags = info[17] as Long
        //padding
        this.release_string = Helper.toCString(info[19] as ByteArray)
    }

    fun encode(): ByteArray {
        return Struct(FORMAT_STRING).pack(
                magic.toByteArray(),
                this.required_libavb_version_major, this.required_libavb_version_minor,
                this.authentication_data_block_size, this.auxiliary_data_block_size,
                this.algorithm_type,
                this.hash_offset, this.hash_size,
                this.signature_offset, this.signature_size,
                this.public_key_offset, this.public_key_size,
                this.public_key_metadata_offset, this.public_key_metadata_size,
                this.descriptors_offset, this.descriptors_size,
                this.rollback_index,
                this.flags,
                null,
                this.release_string.toByteArray(),
                null,
                null)
    }

    companion object {
        const val magic: String = "AVB0"
        const val SIZE = 256
        const val REVERSED0 = 4
        const val REVERSED = 80
        const val FORMAT_STRING = ("!4s2L2QL11QL${REVERSED0}x47sx" + "${REVERSED}x")

        init {
            Assert.assertEquals(SIZE, Struct(FORMAT_STRING).calcsize())
        }
    }
}