package cfig.bootloader_message

import cfig.io.Struct3
import org.slf4j.LoggerFactory
import java.io.FileInputStream

@OptIn(ExperimentalUnsignedTypes::class)
class BootloaderMsgAB( //offset 2k, size 2k
        var slotSuffix: String = "",
        var updateChannel: String = "",
        var reserved: ByteArray = byteArrayOf()
) {
    companion object {
        private const val FORMAT_STRING = "32s128s1888b"
        const val SIZE = 2048
        private val log = LoggerFactory.getLogger(BootloaderMsgAB::class.java.simpleName)

        init {
            assert(SIZE == Struct3(FORMAT_STRING).calcSize())
        }
    }

    constructor(fis: FileInputStream) : this() {
        val info = Struct3(FORMAT_STRING).unpack(fis)
        this.slotSuffix = info[0] as String
        this.updateChannel = info[1] as String
        this.reserved = info[2] as ByteArray
    }

    fun encode(): ByteArray {
        return Struct3(FORMAT_STRING).pack(
                this.slotSuffix,
                this.updateChannel,
                byteArrayOf())
    }
}
