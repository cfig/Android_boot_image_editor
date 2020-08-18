package cfig.bootloader_message

class BootControl {
    data class SlotMetadata(//size: 16
            var priority: Int = 0,
            var triesRemaining: Int = 0,
            var successfulBoot: Boolean = false,
            var verityCorrupted: Boolean = false,
            var reserved: ByteArray = byteArrayOf()
    )

    class BootloaderControl(
            var slotSuffix: String = "",
            var magic: ByteArray = byteArrayOf(),
            var version: Int = 0,
            var slots: Int = 0,
            var recoveryTriesRemaining: Int = 0,
            var mergeStatus: Int = 0,
            var slotMetaData: ByteArray= byteArrayOf(),
            var reserved: ByteArray = byteArrayOf(),
            var crc32: Int = 0
    )
}
