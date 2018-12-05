package avb.desc

import cfig.Helper
import cfig.io.Struct
import org.apache.commons.codec.binary.Hex
import org.junit.Assert
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.InputStream

class UnknownDescriptor(var data: ByteArray = byteArrayOf()) : Descriptor(0, 0, 0) {
    @Throws(IllegalArgumentException::class)
    constructor(stream: InputStream, seq: Int = 0) : this() {
        this.sequence = seq
        val info = Struct(FORMAT).unpack(stream)
        this.tag = info[0] as Long
        this.num_bytes_following = info[1] as Long
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

    fun analyze(): Any {
        return when (this.tag) {
            0L -> {
                PropertyDescriptor(ByteArrayInputStream(this.encode()), this.sequence)
            }
            1L -> {
                HashTreeDescriptor(ByteArrayInputStream(this.encode()), this.sequence)
            }
            2L -> {
                HashDescriptor(ByteArrayInputStream(this.encode()), this.sequence)
            }
            3L -> {
                KernelCmdlineDescriptor(ByteArrayInputStream(this.encode()), this.sequence)
            }
            4L -> {
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

        fun parseDescriptors(stream: InputStream, totalSize: Long): List<UnknownDescriptor> {
            log.debug("Parse descriptors stream, SIZE = $totalSize")
            val ret: MutableList<UnknownDescriptor> = mutableListOf()
            var currentSize = 0L
            while (true) {
                val desc = UnknownDescriptor(stream)
                currentSize += desc.data.size + SIZE
                log.debug("current SIZE = $currentSize")
                ret.add(desc)
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

        fun parseDescriptors2(stream: InputStream, totalSize: Long): List<Any> {
            log.info("Parse descriptors stream, SIZE = $totalSize")
            val ret: MutableList<Any> = mutableListOf()
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
            Assert.assertEquals(SIZE, Struct(FORMAT).calcSize())
        }
    }
}
