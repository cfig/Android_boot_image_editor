// Copyright 2019-2022 yuyezhong@gmail.com
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package avb.blob

import cfig.Avb
import cc.cfig.io.Struct
import java.io.ByteArrayInputStream
import java.io.InputStream

//avbtool::AvbVBMetaHeader
data class Header(
        var required_libavb_version_major: Int = Avb.AVB_VERSION_MAJOR,
        var required_libavb_version_minor: Int = 0,
        var authentication_data_block_size: Long = 0,
        var auxiliary_data_block_size: Long = 0,
        var algorithm_type: Int = 0,
        var hash_offset: Long = 0,
        var hash_size: Long = 0,
        var signature_offset: Long = 0,
        var signature_size: Long = 0,
        var public_key_offset: Long = 0,
        var public_key_size: Long = 0,
        var public_key_metadata_offset: Long = 0,
        var public_key_metadata_size: Long = 0,
        var descriptors_offset: Long = 0,
        var descriptors_size: Long = 0,
        var rollback_index: Long = 0,
        var flags: Int = 0,
        var release_string: String = "avbtool ${Avb.AVB_VERSION_MAJOR}.${Avb.AVB_VERSION_MINOR}.${Avb.AVB_VERSION_SUB}") {
    @Throws(IllegalArgumentException::class)
    constructor(iS: InputStream) : this() {
        val info = Struct(FORMAT_STRING).unpack(iS)
        check(22 == info.size)
        if (info[0] != magic) {
            throw IllegalArgumentException("stream doesn't look like valid VBMeta Header")
        }
        this.required_libavb_version_major = (info[1] as UInt).toInt()
        this.required_libavb_version_minor = (info[2] as UInt).toInt()
        this.authentication_data_block_size = (info[3] as ULong).toLong()
        this.auxiliary_data_block_size = (info[4] as ULong).toLong()
        this.algorithm_type = (info[5] as UInt).toInt()
        this.hash_offset = (info[6] as ULong).toLong()
        this.hash_size = (info[7] as ULong).toLong()
        this.signature_offset = (info[8] as ULong).toLong()
        this.signature_size = (info[9] as ULong).toLong()
        this.public_key_offset = (info[10] as ULong).toLong()
        this.public_key_size = (info[11] as ULong).toLong()
        this.public_key_metadata_offset = (info[12] as ULong).toLong()
        this.public_key_metadata_size = (info[13] as ULong).toLong()
        this.descriptors_offset = (info[14] as ULong).toLong()
        this.descriptors_size = (info[15] as ULong).toLong()
        this.rollback_index = (info[16] as ULong).toLong()
        this.flags = (info[17] as UInt).toInt()
        //padding: info[18]
        this.release_string = info[19] as String
    }

    @Throws(IllegalArgumentException::class)
    constructor(data: ByteArray) : this(ByteArrayInputStream(data))

    fun encode(): ByteArray {
        return Struct(FORMAT_STRING).pack(
                magic,                                                                  //4s
                this.required_libavb_version_major, this.required_libavb_version_minor, //2L
                this.authentication_data_block_size, this.auxiliary_data_block_size,    //2Q
                this.algorithm_type,                                                    //L
                this.hash_offset, this.hash_size,                                       //hash 2Q
                this.signature_offset, this.signature_size,                             //sig 2Q
                this.public_key_offset, this.public_key_size,                           //pubkey 2Q
                this.public_key_metadata_offset, this.public_key_metadata_size,         //pkmd 2Q
                this.descriptors_offset, this.descriptors_size,                         //desc 2Q
                this.rollback_index,                                                    //Q
                this.flags,                                                             //L
                null,                                                                   //${REVERSED0}x
                this.release_string,                                                    //47s
                null,                                                                   //x
                null)                                                                   //${REVERSED}x
    }

    fun bump_required_libavb_version_minor(minor: Int) {
        this.required_libavb_version_minor = maxOf(required_libavb_version_minor, minor)
    }

    //toplevel flags
    enum class AvbVBMetaImageFlags(val inFlags: Int) {
        AVB_VBMETA_IMAGE_FLAGS_HASHTREE_DISABLED(1),          //disable hashtree image verification, for system/vendor/product etc.
        AVB_VBMETA_IMAGE_FLAGS_VERIFICATION_DISABLED(2 shl 1) //disable all verification, do not parse descriptors
    }

    //verify flags
    enum class AvbSlotVerifyFlags(val inFlags: Int) {
        AVB_SLOT_VERIFY_FLAGS_NONE(0),
        AVB_SLOT_VERIFY_FLAGS_ALLOW_VERIFICATION_ERROR(1),
        AVB_SLOT_VERIFY_FLAGS_RESTART_CAUSED_BY_HASHTREE_CORRUPTION(2),
        AVB_SLOT_VERIFY_FLAGS_NO_VBMETA_PARTITION(4)
    }

    //hash descriptor flags
    enum class HashDescriptorFlags(val inFlags: Int) {
        AVB_HASH_DESCRIPTOR_FLAGS_DO_NOT_USE_AB(1)
    }

    //hash tree descriptor flags
    enum class HashTreeDescriptorFlags(val inFlags: Int) {
        AVB_HASHTREE_DESCRIPTOR_FLAGS_DO_NOT_USE_AB(1)
    }

    companion object {
        private const val magic: String = "AVB0"
        const val SIZE = 256
        private const val REVERSED0 = 4
        private const val REVERSED = 80
        private const val FORMAT_STRING = ("!4s2L2QL11QL${REVERSED0}x47sx" + "${REVERSED}x")

        init {
            check(SIZE == Struct(FORMAT_STRING).calcSize())
        }
    }
}
