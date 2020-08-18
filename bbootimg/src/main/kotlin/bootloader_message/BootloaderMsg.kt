package cfig.bootloader_message

import cfig.io.Struct3
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.lang.IllegalStateException

@OptIn(ExperimentalUnsignedTypes::class)
data class BootloaderMsg(//offset 0, size 2k
        var command: String = "",
        var status: String = "",
        var recovery: String = "",
        var stage: String = "",
        var reserved: ByteArray = byteArrayOf()
) {
    companion object {
        private const val FORMAT_STRING = "32s32s768s32s1184b"
        private const val miscFile = "misc.file"
        const val SIZE = 2048
        private val log = LoggerFactory.getLogger("BootloaderMsg")

        init {
            assert(SIZE == Struct3(FORMAT_STRING).calcSize())
        }
    }

    constructor(fis: FileInputStream) : this() {
        val info = Struct3(FORMAT_STRING).unpack(fis)
        this.command = info[0] as String
        this.status = info[1] as String
        this.recovery = info[2] as String
        this.stage = info[3] as String
        this.reserved = info[4] as ByteArray
    }

    fun encode(): ByteArray {
        return Struct3(FORMAT_STRING).pack(
                this.command,
                this.stage,
                this.recovery,
                this.stage,
                byteArrayOf())
    }

    fun clearBootloaderMessage() {
        val boot = BootloaderMsg()
        boot.writeBootloaderMessage()
    }

    fun writeBootloaderMessage(options: Array<String>) {
        this.updateBootloaderMessageInStruct(options)
        this.writeBootloaderMessage()
    }

    fun readBootloaderMsg() {
        if (File(miscFile).exists()) {
            log.info("readBootloaderMsg() from $miscFile")
            val fis = FileInputStream(miscFile)
            val info = Struct3(FORMAT_STRING).unpack(fis)
            this.command = info[0] as String
            this.status = info[1] as String
            this.recovery = info[2] as String
            this.stage = info[3] as String
            this.reserved = info[4] as ByteArray
        } else {
            log.info("$miscFile missing")
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
        if (!File(miscFile).exists()) {
            File(miscFile).createNewFile()
        }
        FileOutputStream(miscFile, false).use { fos ->
            fos.write(this.encode())
        }
    }

    fun updateBootloaderMessageInStruct(options: Array<String>) {
        this.command = "boot-recovery"
        this.recovery = "recovery\n"
        options.forEach {
            this.recovery += if (it.endsWith("\n")) {
                it
            } else {
                it + "\n"
            }
        }
    }

    fun updateBootloaderMessage(command: String, recovery: String, options: Array<String>?) {
        this.command = command
        this.recovery = "$recovery\n"
        options?.forEach {
            this.recovery += if (it.endsWith("\n")) {
                it
            } else {
                it + "\n"
            }
        }
    }

    fun updateBootFastboot() {
        this.command = "boot-fastboot"
        this.recovery = ""
    }
}
