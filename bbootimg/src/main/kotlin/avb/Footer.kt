package avb

import cfig.io.Struct3
import org.junit.Assert
import java.io.InputStream
/*
https://github.com/cfig/Android_boot_image_editor/blob/master/doc/layout.md#32-avb-footer-vboot-20

+---------------------------------------+-------------------------+ --> partition_size - block_size
| Padding                               | block_size - 64         |
+---------------------------------------+-------------------------+ --> partition_size - 64
| AVB Footer                            | total 64                |
|                                       |                         |
|   - Footer Magic "AVBf"               |     4                   |
|   - Footer Major Version              |     4                   |
|   - Footer Minor Version              |     4                   |
|   - Original image size               |     8                   |
|   - VBMeta offset                     |     8                   |
|   - VBMeta size                       |     8                   |
|   - Padding                           |     28                  |
+---------------------------------------+-------------------------+ --> partition_size
 */

data class Footer constructor(
        var versionMajor: UInt = FOOTER_VERSION_MAJOR,
        var versionMinor: UInt = FOOTER_VERSION_MINOR,
        var originalImageSize: ULong = 0U,
        var vbMetaOffset: ULong = 0U,
        var vbMetaSize: ULong = 0U
) {
    companion object {
        const val MAGIC = "AVBf"
        const val SIZE = 64
        private const val RESERVED = 28
        const val FOOTER_VERSION_MAJOR = 1U
        const val FOOTER_VERSION_MINOR = 0U
        private const val FORMAT_STRING = "!4s2L3Q${RESERVED}x"

        init {
            Assert.assertEquals(SIZE, Struct3(FORMAT_STRING).calcSize())
        }
    }

    @Throws(IllegalArgumentException::class)
    constructor(iS: InputStream) : this() {
        val info = Struct3(FORMAT_STRING).unpack(iS)
        Assert.assertEquals(7, info.size)
        if (MAGIC != (info[0] as String)) {
            throw IllegalArgumentException("stream doesn't look like valid AVB Footer")
        }
        versionMajor = info[1] as UInt
        versionMinor = info[2] as UInt
        originalImageSize = info[3] as ULong
        vbMetaOffset = info[4] as ULong
        vbMetaSize = info[5] as ULong
    }

    fun encode(): ByteArray {
        return Struct3(FORMAT_STRING).pack(MAGIC,
                this.versionMajor,
                this.versionMinor,
                this.originalImageSize,
                this.vbMetaOffset,
                this.vbMetaSize,
                null)
    }
}