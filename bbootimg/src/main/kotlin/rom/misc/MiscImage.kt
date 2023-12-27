package rom.misc

import cc.cfig.io.Struct
import cfig.helper.Helper
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.io.InputStream

data class MiscImage(
    var bcb: BootloaderMessage = BootloaderMessage(),
    var mbcb: MiscBootControl? = null,
    var virtualAB: VirtualABMessage? = null
) {
    companion object {
        private val log = LoggerFactory.getLogger(MiscImage::class.java)

        fun parse(fileName: String): MiscImage {
            val ret = MiscImage()
            FileInputStream(fileName).use { fis ->
                ret.bcb = BootloaderMessage(fis)
            }
            FileInputStream(fileName).use { fis ->
                fis.skip(VirtualABMessage.OFFSET)
                try {
                    ret.virtualAB = VirtualABMessage(fis)
                } catch (e: IllegalArgumentException) {
                    log.info(e.toString())
                }
            }
            FileInputStream(fileName).use { fis ->
                fis.skip(MiscBootControl.OFFSET)
                try {
                    ret.mbcb = MiscBootControl(fis)
                } catch (e: IllegalArgumentException) {
                    log.info(e.toString())
                }
            }
            return ret
        }
    }

    //offset 0, size 2k
    data class BootloaderMessage(
        var command: String = "",
        var status: String = "",
        var recovery: String = "",
        var stage: String = "",
        var reserved: ByteArray = byteArrayOf()
    ) {
        constructor(fis: FileInputStream) : this() {
            val info = Struct(FORMAT_STRING).unpack(fis)
            this.command = info[0] as String
            this.status = info[1] as String
            this.recovery = info[2] as String
            this.stage = info[3] as String
            this.reserved = info[4] as ByteArray
        }

        fun encode(): ByteArray {
            return Struct(FORMAT_STRING).pack(
                this.command,
                this.stage,
                this.recovery,
                this.stage,
                this.reserved,
            )
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

        companion object {
            private const val FORMAT_STRING = "32s32s768s32s1184b"
            const val SIZE = 2048

            init {
                check(SIZE == Struct(FORMAT_STRING).calcSize())
            }

            /*
                https://android-review.googlesource.com/c/platform/bootable/recovery/+/735984
             */
            fun rebootFastboot1(): BootloaderMessage {
                return BootloaderMessage().apply {
                    command = "boot-fastboot"
                }
            }

            fun rebootFastboot2(): BootloaderMessage {
                return BootloaderMessage().apply {
                    updateBootloaderMessageInStruct(arrayOf("--fastboot"))
                }
            }

            fun rebootBootloader(): BootloaderMessage {
                return BootloaderMessage().apply {
                    command = "bootonce-bootloader"
                }
            }

            fun rebootRecovery(): BootloaderMessage {
                return BootloaderMessage().apply {
                    this.updateBootloaderMessageInStruct(arrayOf())
                }
            }

            fun rebootCrash(): BootloaderMessage {
                return BootloaderMessage().apply {
                    //@formatter:off
                    updateBootloaderMessageInStruct(arrayOf(
                        "--prompt_and_wipe_data",
                        "--reason=RescueParty",
                        "--locale=en_US"))
                    //@formatter:on
                }
            }

            fun rebootOTA(): BootloaderMessage {
                return BootloaderMessage().apply {
                    updateBootloaderMessageInStruct(arrayOf("--update_package=/cache/update.zip", "--security"))
                }
            }

            fun rebootWipeData(): BootloaderMessage {
                return BootloaderMessage().apply {
                    //@formatter:off
                    updateBootloaderMessageInStruct(arrayOf(
                        "--wipe_data",
                        "--reason=convert_fbe",
                        "--locale=en_US"))
                    //@formatter:on
                }
            }

            fun rebootWipeAb(): BootloaderMessage {
                return BootloaderMessage().apply {
                    //@formatter:off
                    updateBootloaderMessageInStruct(arrayOf(
                        "--wipe_ab",
                        "--wipe_package_size=1024",
                        "--locale=en_US"))
                    //@formatter:on
                }
            }

            fun generateSamples(): MutableList<BootloaderMessage> {
                return mutableListOf(
                    rebootFastboot1(),
                    rebootFastboot2(),
                    rebootBootloader(),
                    rebootRecovery(),
                    rebootCrash(),
                    rebootOTA(),
                    rebootWipeData(),
                    rebootWipeAb()
                )
            }
        }
    }

    data class MiscSlotMetadata(
        var priority: Int = 0,
        var tries_remaining: Int = 0,
        var successful_boot: Int = 0,
        var verity_corrupted: Int = 0,
        var reserved: Int = 0,
    ) {
        constructor(inS: InputStream) : this() {
            val info = Struct(FORMAT_STRING).unpack(inS)
            priority = (info[0] as ByteArray)[0].toInt()
            tries_remaining = (info[1] as ByteArray)[0].toInt()
            successful_boot = (info[2] as ByteArray)[0].toInt()
            verity_corrupted = (info[3] as ByteArray)[0].toInt()
            reserved = (info[4] as ByteArray)[0].toInt()
        }

        fun encode(): ByteArray {
            return Struct(FORMAT_STRING).pack(
                byteArrayOf(this.priority.toByte()),
                byteArrayOf(this.tries_remaining.toByte()),
                byteArrayOf(this.successful_boot.toByte()),
                byteArrayOf(this.verity_corrupted.toByte()),
                byteArrayOf(this.reserved.toByte())
            )
        }

        companion object {
            private const val FORMAT_STRING = "bbbbb"
            const val SIZE = 5
        }

        init {
            check(SIZE == Struct(FORMAT_STRING).calcSize())
        }
    }

    //offset 4KB, size 32B
    data class MiscBootControl(
        var magic: ByteArray = byteArrayOf(),
        var version: Int = 0,
        var recovery_tries_remaining: Int = 0,
        var slot_info: MutableList<MiscSlotMetadata> = mutableListOf(),
        var reserved: ByteArray = byteArrayOf(),
    ) {
        constructor(inS: InputStream) : this() {
            val info = Struct(FORMAT_STRING).unpack(inS)
            this.magic = info[0] as ByteArray
            if (MAGIC != Helper.Companion.toHexString(this.magic)) {
                log.warn(Helper.Companion.toHexString(this.magic))
                throw IllegalArgumentException("stream is not MiscBootControl")
            }
            this.version = (info[1] as ByteArray)[0].toInt()
            this.recovery_tries_remaining = (info[2] as ByteArray)[0].toInt()
            this.slot_info = mutableListOf(
                MiscSlotMetadata(ByteArrayInputStream(info[3] as ByteArray)),
                MiscSlotMetadata(ByteArrayInputStream(info[4] as ByteArray))
            )
            this.reserved = info[5] as ByteArray
        }

        fun encode(): ByteArray {
            return Struct(FORMAT_STRING).pack(
                this.magic,
                byteArrayOf(this.version.toByte()),
                byteArrayOf(this.recovery_tries_remaining.toByte()),
                this.slot_info.get(0).encode(),
                this.slot_info.get(1).encode(),
                this.reserved,
            )
        }

        companion object {
            private const val FORMAT_STRING = "4bbb${MiscSlotMetadata.SIZE}b${MiscSlotMetadata.SIZE}b16b"
            private const val MAGIC = "00414242"
            private const val SIZE = 32
            const val OFFSET = 4 * 1024L

            init {
                check(SIZE == Struct(FORMAT_STRING).calcSize())
            }
        }
    }

    //offset 32KB, size 64B
    data class VirtualABMessage(
        var version: Int = 0,
        var magic: ByteArray = byteArrayOf(),
        var mergeStatus: Int = 0,
        var sourceSlot: Int = 0,
        var reserved: ByteArray = byteArrayOf()
    ) {
        companion object {
            private const val FORMAT_STRING = "b4bbb57b"
            private const val MAGIC = "b00a7456"
            const val SIZE = 64
            const val OFFSET = 32 * 1024L

            init {
                check(SIZE == Struct(FORMAT_STRING).calcSize())
            }
        }

        constructor(fis: FileInputStream) : this() {
            val info = Struct(FORMAT_STRING).unpack(fis)
            this.version = (info[0] as ByteArray)[0].toInt()
            this.magic = info[1] as ByteArray
            this.mergeStatus = (info[2] as ByteArray)[0].toInt()
            this.sourceSlot = (info[3] as ByteArray)[0].toInt()
            this.reserved = info[4] as ByteArray
            if (MAGIC != Helper.Companion.toHexString(this.magic)) {
                throw IllegalArgumentException("stream is not VirtualAB message")
            }
        }

        fun encode(): ByteArray {
            return Struct(FORMAT_STRING).pack(
                byteArrayOf(this.version.toByte()),
                this.magic,
                byteArrayOf(this.mergeStatus.toByte()),
                byteArrayOf(this.sourceSlot.toByte()),
                byteArrayOf(0)
            )
        }

        override fun toString(): String {
            return "VABMsg(v=$version, magic=${Helper.toHexString(magic)}, mergeStatus=$mergeStatus:${
                MergeStatus.values().get(this.mergeStatus)
            }, sourceSlot=$sourceSlot)"
        }

        enum class MergeStatus(val status: Int) {
            NONE(0),
            UNKNOWN(1),
            SNAPSHOTTED(2),
            MERGING(3),
            CANCELLED(4)
        }
    }
}
