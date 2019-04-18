package cfig.bootloader_message

import cfig.io.Struct3
import org.junit.Assert
import org.slf4j.LoggerFactory
import java.lang.IllegalStateException

data class BootloaderMsg(
        var command: String = "",
        var status: String = "",
        var recovery: String = "",
        var stage: String = "",
        var reserved: ByteArray = byteArrayOf()
) {
    companion object {
        private const val FORMAT_STRING = "32s32s768s32s1184b"
        const val SIZE = 2048
        private val log = LoggerFactory.getLogger("BootloaderMsg")

        init {
            Assert.assertEquals(SIZE, Struct3(FORMAT_STRING).calcSize())
        }

        fun writeBootloaderMessage(options: Array<String>) {
            val boot = BootloaderMsg()
            boot.updateBootloaderMessageInStruct(options)
            boot.writeBootloaderMessage()
        }
    }

    fun writeRebootBootloader() {
        if (this.command.isNotBlank()) {
            throw IllegalStateException("Bootloader command pending.")
        }
        this.command = "bootonce-bootloader"
        writeBootloaderMessage()
    }

    fun writeBootloaderMessage() {
        log.info("writing ... $this")
    }

    fun updateBootloaderMessageInStruct(options: Array<String>) {
        this.command = "boot-recovery"
        this.recovery = "recovery"
        options.forEach {
            this.recovery += if (it.endsWith("\n")) {
                it
            } else {
                it + "\n"
            }
        }
    }
}
