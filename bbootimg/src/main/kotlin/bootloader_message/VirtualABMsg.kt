package cfig.bootloader_message

import cfig.io.Struct3
import org.slf4j.LoggerFactory
import java.io.FileInputStream

@OptIn(ExperimentalUnsignedTypes::class)
data class VirtualABMsg(
        var version: Int = 0,
        var magic: ByteArray = byteArrayOf(),
        var mergeStatus: Int = 0,
        var sourceSlot: Int = 0,
        var reserved: ByteArray = byteArrayOf()
) {
    companion object {
        private const val FORMAT_STRING = "b4bbb57b"
        const val SIZE = 64
        private val log = LoggerFactory.getLogger("VirtualABMsg")

        init {
            assert(SIZE == Struct3(FORMAT_STRING).calcSize())
        }
    }

    constructor(fis: FileInputStream) : this() {
        val info = Struct3(FORMAT_STRING).unpack(fis)
        this.version = info[0] as Int
        this.magic = info[1] as ByteArray
        this.mergeStatus = info[2] as Int
        this.sourceSlot = info[3] as Int
        this.reserved = info[4] as ByteArray
    }

    fun encode(): ByteArray {
        return Struct3(FORMAT_STRING).pack(
                this.version,
                this.magic,
                this.mergeStatus,
                this.sourceSlot,
                0)
    }
}
