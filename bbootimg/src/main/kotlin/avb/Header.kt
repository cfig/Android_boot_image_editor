package avb

import cfig.Avb
import cfig.Helper
import cfig.io.Struct3
import org.junit.Assert
import java.io.InputStream

//avbtool::AvbVBMetaHeader
data class Header(
        var required_libavb_version_major: UInt = Avb.AVB_VERSION_MAJOR,
        var required_libavb_version_minor: UInt = 0U,
        var authentication_data_block_size: ULong = 0U,
        var auxiliary_data_block_size: ULong = 0U,
        var algorithm_type: UInt = 0U,
        var hash_offset: ULong = 0U,
        var hash_size: ULong = 0U,
        var signature_offset: ULong = 0U,
        var signature_size: ULong = 0U,
        var public_key_offset: ULong = 0U,
        var public_key_size: ULong = 0U,
        var public_key_metadata_offset: ULong = 0U,
        var public_key_metadata_size: ULong = 0U,
        var descriptors_offset: ULong = 0U,
        var descriptors_size: ULong = 0U,
        var rollback_index: ULong = 0U,
        var flags: UInt = 0U,
        var release_string: String = "avbtool ${Avb.AVB_VERSION_MAJOR}.${Avb.AVB_VERSION_MINOR}.${Avb.AVB_VERSION_SUB}") {
    @Throws(IllegalArgumentException::class)
    constructor(iS: InputStream) : this() {
        val info = Struct3(FORMAT_STRING).unpack(iS)
        Assert.assertEquals(22, info.size)
        if (info[0] != magic) {
            throw IllegalArgumentException("stream doesn't look like valid VBMeta Header")
        }
        this.required_libavb_version_major = info[1] as UInt
        this.required_libavb_version_minor = info[2] as UInt
        this.authentication_data_block_size = info[3] as ULong
        this.auxiliary_data_block_size = info[4] as ULong
        this.algorithm_type = info[5] as UInt
        this.hash_offset = info[6] as ULong
        this.hash_size = info[7] as ULong
        this.signature_offset = info[8] as ULong
        this.signature_size = info[9] as ULong
        this.public_key_offset = info[10] as ULong
        this.public_key_size = info[11] as ULong
        this.public_key_metadata_offset = info[12] as ULong
        this.public_key_metadata_size = info[13] as ULong
        this.descriptors_offset = info[14] as ULong
        this.descriptors_size = info[15] as ULong
        this.rollback_index = info[16] as ULong
        this.flags = info[17] as UInt
        //padding: info[18]
        this.release_string = info[19] as String
    }

    fun encode(): ByteArray {
        return Struct3(FORMAT_STRING).pack(
                magic,
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
                null, //${REVERSED0}x
                this.release_string, //47s
                null, //x
                null) //${REVERSED}x
    }

    fun bump_required_libavb_version_minor(minor: UInt) {
        this.required_libavb_version_minor = maxOf(required_libavb_version_minor, minor)
    }

    companion object {
        const val magic: String = "AVB0"
        const val SIZE = 256
        private const val REVERSED0 = 4
        private const val REVERSED = 80
        const val FORMAT_STRING = ("!4s2L2QL11QL${REVERSED0}x47sx" + "${REVERSED}x")

        init {
            Assert.assertEquals(SIZE, Struct3(FORMAT_STRING).calcSize())
        }
    }
}