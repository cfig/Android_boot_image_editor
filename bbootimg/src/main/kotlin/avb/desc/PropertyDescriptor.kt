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

class PropertyDescriptor(
        var key: String = "",
        var value: String = "") : Descriptor(TAG, 0, 0) {
    override fun encode(): ByteArray {
        if (SIZE != Struct(FORMAT_STRING).calcSize().toUInt()) {
            throw RuntimeException("PropertyDesc size check failed")
        }
        this.num_bytes_following = (SIZE + this.key.length.toUInt() + this.value.length.toUInt() + 2U - 16U).toLong()
        val nbfWithPadding = Helper.round_to_multiple(this.num_bytes_following.toLong(), 8).toULong()
        val paddingSize = nbfWithPadding - num_bytes_following.toUInt()
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
        this.tag = (info[0] as ULong).toLong()
        this.num_bytes_following = (info[1] as ULong).toLong()
        val keySize = (info[2] as ULong).toUInt()
        val valueSize = (info[3] as ULong).toUInt()
        val expectedSize = Helper.round_to_multiple(SIZE - 16U + keySize + 1U + valueSize + 1U, 8U)
        if (this.tag != TAG || expectedSize.toLong() != this.num_bytes_following) {
            throw IllegalArgumentException("Given data does not look like a |property| descriptor")
        }
        this.sequence = seq

        val info2 = Struct("${keySize}sx${valueSize}s").unpack(data)
        this.key = info2[0] as String
        this.value = info2[2] as String
    }

    companion object {
        const val TAG: Long = 0L
        const val SIZE = 32U
        const val FORMAT_STRING = "!4Q"
    }
}
