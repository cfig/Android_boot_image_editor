package avb.blob

import cfig.io.Struct3
import java.io.File
import java.io.FileInputStream
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

@ExperimentalUnsignedTypes
data class Footer constructor(
        var versionMajor: UInt = FOOTER_VERSION_MAJOR,
        var versionMinor: UInt = FOOTER_VERSION_MINOR,
        var originalImageSize: ULong = 0U,
        var vbMetaOffset: ULong = 0U,
        var vbMetaSize: ULong = 0U
) {
    @Throws(IllegalArgumentException::class)
    constructor(iS: InputStream) : this() {
        val info = Struct3(FORMAT_STRING).unpack(iS)
        assert(7 == info.size)
        if (MAGIC != (info[0] as String)) {
            throw IllegalArgumentException("stream doesn't look like valid AVB Footer")
        }
        versionMajor = info[1] as UInt
        versionMinor = info[2] as UInt
        originalImageSize = info[3] as ULong
        vbMetaOffset = info[4] as ULong
        vbMetaSize = info[5] as ULong
    }

    constructor(originalImageSize: ULong, vbMetaOffset: ULong, vbMetaSize: ULong)
            : this(FOOTER_VERSION_MAJOR, FOOTER_VERSION_MINOR, originalImageSize, vbMetaOffset, vbMetaSize)

    @Throws(IllegalArgumentException::class)
    constructor(image_file: String) : this() {
        FileInputStream(image_file).use { fis ->
            fis.skip(File(image_file).length() - SIZE)
            val footer = Footer(fis)
            this.versionMajor = footer.versionMajor
            this.versionMinor = footer.versionMinor
            this.originalImageSize = footer.originalImageSize
            this.vbMetaOffset = footer.vbMetaOffset
            this.vbMetaSize = footer.vbMetaSize
        }
    }

    fun encode(): ByteArray {
        return Struct3(FORMAT_STRING).pack(MAGIC, //4s
                this.versionMajor,                //L
                this.versionMinor,                //L
                this.originalImageSize,           //Q
                this.vbMetaOffset,                //Q
                this.vbMetaSize,                  //Q
                null)                             //${RESERVED}x
    }

    companion object {
        private const val MAGIC = "AVBf"
        const val SIZE = 64
        private const val RESERVED = 28
        private const val FOOTER_VERSION_MAJOR = 1U
        private const val FOOTER_VERSION_MINOR = 0U
        private const val FORMAT_STRING = "!4s2L3Q${RESERVED}x"

        init {
            assert(SIZE == Struct3(FORMAT_STRING).calcSize())
        }
    }
}