// Copyright 2021 yuyezhong@gmail.com
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package avb.blob

import cc.cfig.io.Struct
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

data class Footer constructor(
        var versionMajor: Int = FOOTER_VERSION_MAJOR,
        var versionMinor: Int = FOOTER_VERSION_MINOR,
        var originalImageSize: Long = 0,
        var vbMetaOffset: Long = 0,
        var vbMetaSize: Long = 0
) {
    @Throws(IllegalArgumentException::class)
    constructor(iS: InputStream) : this() {
        val info = Struct(FORMAT_STRING).unpack(iS)
        assert(7 == info.size)
        if (MAGIC != (info[0] as String)) {
            throw IllegalArgumentException("stream doesn't look like valid AVB Footer")
        }
        versionMajor = (info[1] as UInt).toInt()
        versionMinor = (info[2] as UInt).toInt()
        originalImageSize = (info[3] as ULong).toLong()
        vbMetaOffset = (info[4] as ULong).toLong()
        vbMetaSize = (info[5] as ULong).toLong()
    }

    constructor(originalImageSize: Long, vbMetaOffset: Long, vbMetaSize: Long)
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
        return Struct(FORMAT_STRING).pack(MAGIC, //4s
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
        private const val FOOTER_VERSION_MAJOR = 1
        private const val FOOTER_VERSION_MINOR = 0
        private const val FORMAT_STRING = "!4s2L3Q${RESERVED}x"

        init {
            assert(SIZE == Struct(FORMAT_STRING).calcSize())
        }
    }
}
