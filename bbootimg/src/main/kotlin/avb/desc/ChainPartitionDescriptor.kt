// Copyright 2021 yuyezhong@gmail.com
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

import avb.AVBInfo
import cfig.Avb
import cfig.helper.Helper
import cfig.helper.Dumpling
import cc.cfig.io.Struct
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import org.slf4j.LoggerFactory

class ChainPartitionDescriptor(
        var rollback_index_location: Int = 0,
        var partition_name_len: Int = 0,
        var public_key_len: Int = 0,
        var partition_name: String = "",
        var pubkey: ByteArray = byteArrayOf(),
        var pubkey_sha1: String = ""
) : Descriptor(TAG, 0, 0) {
    override fun encode(): ByteArray {
        this.partition_name_len = this.partition_name.length
        this.public_key_len = this.pubkey.size
        this.num_bytes_following = SIZE + this.partition_name_len + this.public_key_len - 16
        val nbf_with_padding = Helper.round_to_multiple(this.num_bytes_following, 8).toULong()
        val padding_size = nbf_with_padding - this.num_bytes_following.toUInt()
        val desc = Struct(FORMAT_STRING + "${RESERVED}x").pack(
                TAG,
                nbf_with_padding,
                this.rollback_index_location,
                this.partition_name.length.toUInt(),
                this.public_key_len,
                null)
        val padding = Struct("${padding_size}x").pack(null)
        return Helper.join(desc, this.partition_name.toByteArray(), this.pubkey, padding)
    }

    companion object {
        const val TAG: Long = 4L
        const val RESERVED = 64
        const val SIZE = 28L + RESERVED
        const val FORMAT_STRING = "!2Q3L"
        private val log = LoggerFactory.getLogger(ChainPartitionDescriptor::class.java)
    }

    constructor(data: InputStream, seq: Int = 0) : this() {
        if (SIZE - RESERVED != Struct(FORMAT_STRING).calcSize().toLong()) {
            throw RuntimeException("ChainPartitionDescriptor size check failed")
        }
        this.sequence = seq
        val info = Struct(FORMAT_STRING + "${RESERVED}s").unpack(data)
        this.tag = (info[0] as ULong).toLong()
        this.num_bytes_following = (info[1] as ULong).toLong()
        this.rollback_index_location = (info[2] as UInt).toInt()
        this.partition_name_len = (info[3] as UInt).toInt()
        this.public_key_len = (info[4] as UInt).toInt()
        val expectedSize = Helper.round_to_multiple(SIZE - 16 + this.partition_name_len + this.public_key_len, 8)
        if (this.tag != TAG || this.num_bytes_following != expectedSize) {
            throw IllegalArgumentException("Given data does not look like a chain/delegation descriptor")
        }
        val info2 = Struct("${this.partition_name_len}s${this.public_key_len}b").unpack(data)
        this.partition_name = info2[0] as String
        this.pubkey = info2[1] as ByteArray
        val md = MessageDigest.getInstance("SHA1").let {
            it.update(this.pubkey)
            it.digest()
        }
        this.pubkey_sha1 = Helper.toHexString(md)
    }

    fun verify(image_files: List<String>, parent: String = ""): Array<Any> {
        val ret: Array<Any> = arrayOf(false, "file not found")
        for (item in image_files) {
            if (File(item).exists()) {
                val subAi = AVBInfo.parseFrom(Dumpling(item))
                if (pubkey.contentEquals(subAi.auxBlob!!.pubkey!!.pubkey)) {
                    log.info("VERIFY($parent): public key matches, PASS")
                    return Avb.verify(subAi, Dumpling(item), parent)
                } else {
                    log.info("VERIFY($parent): public key mismatch, FAIL")
                    ret[1] = "public key mismatch"
                    return ret
                }
            }
        }
        log.info("VERIFY($parent): " + ret[1] as String + "... FAIL")
        return ret
    }

    override fun toString(): String {
        return "ChainPartitionDescriptor(partition=${this.partition_name}, pubkey=${this.pubkey.contentToString()}"
    }
}
