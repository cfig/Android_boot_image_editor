// Copyright 2018-2022 yuyezhong@gmail.com
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

package avb.desc

import cfig.helper.Helper
import cc.cfig.io.Struct
import java.io.InputStream

class KernelCmdlineDescriptor(
        var flags: Int = 0,
        var cmdlineLength: Int = 0,
        var cmdline: String = "")
    : Descriptor(TAG, 0, 0) {
    var flagsInterpretation: String = ""
        get() {
            var ret = ""
            if (this.flags and flagHashTreeEnabled == flagHashTreeEnabled) {
                ret += "$flagHashTreeEnabled: hashTree Enabled"
            } else if (this.flags and flagHashTreeDisabled == flagHashTreeDisabled) {
                ret += "$flagHashTreeDisabled: hashTree Disabled"
            }
            return ret
        }

    @Throws(IllegalArgumentException::class)
    constructor(data: InputStream, seq: Int = 0) : this() {
        val info = Struct(FORMAT_STRING).unpack(data)
        this.tag = (info[0] as ULong).toLong()
        this.num_bytes_following = (info[1] as ULong).toLong()
        this.flags = (info[2] as UInt).toInt()
        this.cmdlineLength = (info[3] as UInt).toInt()
        this.sequence = seq
        val expectedSize = Helper.round_to_multiple(SIZE - 16 + this.cmdlineLength, 8)
        if ((this.tag != TAG) || (this.num_bytes_following != expectedSize.toLong())) {
            throw IllegalArgumentException("Given data does not look like a kernel cmdline descriptor")
        }
        this.cmdline = Struct("${this.cmdlineLength}s").unpack(data)[0] as String
    }

    override fun encode(): ByteArray {
        val num_bytes_following = SIZE - 16 + cmdline.toByteArray().size
        val nbf_with_padding = Helper.round_to_multiple(num_bytes_following.toLong(), 8)
        val padding_size = nbf_with_padding - num_bytes_following
        val desc = Struct(FORMAT_STRING).pack(
                TAG,
                nbf_with_padding,
                this.flags,
                cmdline.length)
        val padding = Struct("${padding_size}x").pack(null)
        return Helper.join(desc, cmdline.toByteArray(), padding)
    }

    companion object {
        const val TAG: Long = 3L
        const val SIZE = 24
        const val FORMAT_STRING = "!2Q2L" //# tag, num_bytes_following (descriptor header), flags, cmdline length (bytes)
        //AVB_KERNEL_CMDLINE_FLAGS_USE_ONLY_IF_HASHTREE_NOT_DISABLED
        const val flagHashTreeEnabled = 1
        //AVB_KERNEL_CMDLINE_FLAGS_USE_ONLY_IF_HASHTREE_DISABLED
        const val flagHashTreeDisabled = 2

        init {
            check(SIZE == Struct(FORMAT_STRING).calcSize())
        }
    }
}
