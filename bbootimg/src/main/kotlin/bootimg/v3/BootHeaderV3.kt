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

package cfig.bootimg.v3

import cfig.bootimg.Common
import cfig.io.Struct3
import org.slf4j.LoggerFactory
import java.io.InputStream

class BootHeaderV3(
    var kernelSize: Int = 0,
    var ramdiskSize: Int = 0,
    var osVersion: String = "",
    var osPatchLevel: String = "",
    var headerSize: Int = 0,
    var headerVersion: Int = 0,
    var cmdline: String = "",
    var signatureSize: Int = 0
) {
    @Throws(IllegalArgumentException::class)
    constructor(iS: InputStream?) : this() {
        if (iS == null) {
            return
        }
        log.warn("BootImgHeaderV3/V4 constructor")
        val info = Struct3(FORMAT_STRING).unpack(iS)
        assert(12 == info.size)
        if (info[0] != magic) {
            throw IllegalArgumentException("stream doesn't look like Android Boot Image V3 Header")
        }
        this.kernelSize = (info[1] as UInt).toInt()
        this.ramdiskSize = (info[2] as UInt).toInt()
        val osNPatch = info[3] as UInt
        if (0U != osNPatch) { //treated as 'reserved' in this boot image
            this.osVersion = Common.parseOsVersion(osNPatch.toInt() shr 11)
            this.osPatchLevel = Common.parseOsPatchLevel((osNPatch and 0x7ff.toUInt()).toInt())
        }
        this.headerSize = (info[4] as UInt).toInt()
        //5,6,7,8 reserved
        this.headerVersion = (info[9] as UInt).toInt()
        this.cmdline = info[10] as String
        this.signatureSize = (info[11] as UInt).toInt()
        assert(this.headerSize in intArrayOf(BOOT_IMAGE_HEADER_V3_SIZE, BOOT_IMAGE_HEADER_V4_SIZE))
    }

    fun encode(): ByteArray {
        return Struct3(FORMAT_STRING).pack(
            magic,
            kernelSize,
            ramdiskSize,
            (Common.packOsVersion(osVersion) shl 11) or Common.packOsPatchLevel(osPatchLevel),
            headerSize,
            0,
            0,
            0,
            0,
            headerVersion,
            cmdline,
            signatureSize
        )
    }

    fun feature67(): BootHeaderV3 {
        val newHeaderSize = when (headerVersion) {
            3 -> BOOT_IMAGE_HEADER_V3_SIZE
            else -> BOOT_IMAGE_HEADER_V4_SIZE
        }
        if (newHeaderSize != headerSize) {
            log.warn("wrong headerSize, fixed.($headerSize -> $newHeaderSize)")
            headerSize = newHeaderSize
        }
        if (signatureSize != 0 && headerVersion == 3) {
            log.warn("trim bootSignature for headerVersion=3")
            signatureSize = 0
        }
        return this
    }

    override fun toString(): String {
        return "BootImgHeaderV3(kernelSize=$kernelSize, ramdiskSize=$ramdiskSize, osVersion=$osVersion, osPatchLevel=$osPatchLevel, headerSize=$headerSize, headerVersion=$headerVersion, cmdline='$cmdline')"
    }

    companion object {
        internal val log = LoggerFactory.getLogger(BootHeaderV3::class.java)
        const val magic = "ANDROID!"
        const val FORMAT_STRING = "8s" + //"ANDROID!"
                "4I" +    //kernel size, ramdisk size, os_version/patch, header size
                "4I" +    //reserved
                "I" +     //header version
                "1536s" + //cmdline
                "I"       //signature size
        private const val BOOT_IMAGE_HEADER_V3_SIZE = 1580
        private const val BOOT_IMAGE_HEADER_V4_SIZE = 1584
        const val pageSize: Int = 4096

        init {
            assert(BOOT_IMAGE_HEADER_V4_SIZE == Struct3(FORMAT_STRING).calcSize()) {
                "internal error: expected size $BOOT_IMAGE_HEADER_V4_SIZE "
            }
        }
    }
}
