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

import cc.cfig.io.Struct
import cfig.helper.Helper
import org.apache.commons.codec.binary.Hex
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.InputStream

class UnknownDescriptor(var data: ByteArray = byteArrayOf()) : Descriptor(0, 0, 0) {
    @Throws(IllegalArgumentException::class)
    constructor(stream: InputStream, seq: Int = 0) : this() {
        this.sequence = seq
        val info = Struct(FORMAT).unpack(stream)
        this.tag = (info[0] as ULong).toLong()
        this.num_bytes_following = (info[1] as ULong).toLong()
        log.debug("UnknownDescriptor: tag = $tag, len = ${this.num_bytes_following}")
        this.data = ByteArray(this.num_bytes_following.toInt())
        if (this.num_bytes_following.toInt() != stream.read(data)) {
            throw IllegalArgumentException("descriptor SIZE mismatch")
        }
    }

    override fun encode(): ByteArray {
        return Helper.join(Struct(FORMAT).pack(this.tag, this.data.size.toLong()), data)
    }

    override fun toString(): String {
        return "UnknownDescriptor(tag=$tag, SIZE=${data.size}, data=${Hex.encodeHexString(data)}"
    }

    fun analyze(): Descriptor {
        return when (this.tag.toUInt()) {
            0U -> {
                PropertyDescriptor(ByteArrayInputStream(this.encode()), this.sequence)
            }
            1U -> {
                HashTreeDescriptor(ByteArrayInputStream(this.encode()), this.sequence)
            }
            2U -> {
                HashDescriptor(ByteArrayInputStream(this.encode()), this.sequence)
            }
            3U -> {
                KernelCmdlineDescriptor(ByteArrayInputStream(this.encode()), this.sequence)
            }
            4U -> {
                ChainPartitionDescriptor(ByteArrayInputStream(this.encode()), this.sequence)
            }
            else -> {
                this
            }
        }
    }

    companion object {
        private const val SIZE = 16
        private const val FORMAT = "!QQ"
        private val log = LoggerFactory.getLogger(UnknownDescriptor::class.java)

        fun parseDescriptors(stream: InputStream, totalSize: Long): List<Descriptor> {
            log.debug("Parse descriptors stream, SIZE = $totalSize")
            val ret: MutableList<Descriptor> = mutableListOf()
            var currentSize = 0L
            var seq = 0
            while (true) {
                val desc = UnknownDescriptor(stream, ++seq)
                currentSize += desc.data.size + SIZE
                log.debug("current SIZE = $currentSize")
                log.debug(desc.toString())
                ret.add(desc.analyze())
                if (currentSize == totalSize) {
                    log.debug("parse descriptor done")
                    break
                } else if (currentSize > totalSize) {
                    log.error("Read more than expected")
                    throw IllegalStateException("Read more than expected")
                } else {
                    log.debug(desc.toString())
                    log.debug("read another descriptor")
                }
            }
            return ret
        }

        init {
            check(SIZE == Struct(FORMAT).calcSize())
        }
    }
}
