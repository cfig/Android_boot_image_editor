package avb

import cfig.io.Struct
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
        var versionMajor: Long = FOOTER_VERSION_MAJOR,
        var versionMinor: Long = FOOTER_VERSION_MINOR,
        var originalImageSize: Long = 0L,
        var vbMetaOffset: Long = 0L,
        var vbMetaSize: Long = 0L
) {
    companion object {
        const val MAGIC = "AVBf"
        const val SIZE = 64
        private const val RESERVED = 28
        const val FOOTER_VERSION_MAJOR = 1L
        const val FOOTER_VERSION_MINOR = 0L
        private const val FORMAT_STRING = "!4s2L3Q${RESERVED}x"

        init {
            Assert.assertEquals(SIZE, Struct(FORMAT_STRING).calcSize())
        }
    }

    @Throws(IllegalArgumentException::class)
    constructor(iS: InputStream) : this() {
        val info = Struct(FORMAT_STRING).unpack(iS)
        Assert.assertEquals(7, info.size)
        if (!MAGIC.toByteArray().contentEquals(info[0] as ByteArray)) {
            throw IllegalArgumentException("stream doesn't look like valid AVB Footer")
        }
        versionMajor = info[1] as Long
        versionMinor = info[2] as Long
        originalImageSize = info[3] as Long
        vbMetaOffset = info[4] as Long
        vbMetaSize = info[5] as Long
    }

    fun encode(): ByteArray {
        return Struct(FORMAT_STRING).pack(Footer.MAGIC.toByteArray(),
                this.versionMajor,
                this.versionMinor,
                this.originalImageSize,
                this.vbMetaOffset,
                this.vbMetaSize,
                null)
    }
}