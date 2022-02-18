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

package cfig.bootimg.v2

import cc.cfig.io.Struct3
import cfig.helper.Helper
import cfig.bootimg.Common
import org.slf4j.LoggerFactory
import java.io.InputStream
import kotlin.math.pow

open class BootHeaderV2(
        var kernelLength: Int = 0,
        var kernelOffset: Long = 0L, //UInt

        var ramdiskLength: Int = 0,
        var ramdiskOffset: Long = 0L, //UInt

        var secondBootloaderLength: Int = 0,
        var secondBootloaderOffset: Long = 0L, //UInt

        var recoveryDtboLength: Int = 0,
        var recoveryDtboOffset: Long = 0L,//Q

        var dtbLength: Int = 0,
        var dtbOffset: Long = 0L,//Q

        var tagsOffset: Long = 0L, //UInt

        var pageSize: Int = 0,

        var headerSize: Int = 0,
        var headerVersion: Int = 0,

        var board: String = "",

        var cmdline: String = "",

        var hash: ByteArray? = null,

        var osVersion: String? = null,
        var osPatchLevel: String? = null) {
    @Throws(IllegalArgumentException::class)
    constructor(iS: InputStream?) : this() {
        if (iS == null) {
            return
        }
        log.warn("BootImgHeader constructor")
        val info = Struct3(FORMAT_STRING).unpack(iS)
        assert(20 == info.size)
        if (info[0] != magic) {
            throw IllegalArgumentException("stream doesn't look like Android Boot Image Header")
        }
        this.kernelLength = (info[1] as UInt).toInt()
        this.kernelOffset = (info[2] as UInt).toLong()
        this.ramdiskLength = (info[3] as UInt).toInt()
        this.ramdiskOffset = (info[4] as UInt).toLong()
        this.secondBootloaderLength = (info[5] as UInt).toInt()
        this.secondBootloaderOffset = (info[6] as UInt).toLong()
        this.tagsOffset = (info[7] as UInt).toLong()
        this.pageSize = (info[8] as UInt).toInt()
        this.headerVersion = (info[9] as UInt).toInt()
        val osNPatch = info[10] as UInt
        if (0U != osNPatch) { //treated as 'reserved' in this boot image
            this.osVersion = Common.parseOsVersion(osNPatch.toInt() shr 11)
            this.osPatchLevel = Common.parseOsPatchLevel((osNPatch and 0x7ff.toUInt()).toInt())
        }
        this.board = info[11] as String
        this.cmdline = (info[12] as String) + (info[14] as String)
        this.hash = info[13] as ByteArray

        if (this.headerVersion > 0) {
            this.recoveryDtboLength = (info[15] as UInt).toInt()
            this.recoveryDtboOffset = (info[16] as ULong).toLong()
        }

        this.headerSize = (info[17] as UInt).toInt()
        assert(this.headerSize.toInt() in intArrayOf(BOOT_IMAGE_HEADER_V2_SIZE,
                BOOT_IMAGE_HEADER_V1_SIZE, BOOT_IMAGE_HEADER_V0_SIZE)) {
            "header size ${this.headerSize} illegal"
        }

        if (this.headerVersion > 1) {
            this.dtbLength = (info[18] as UInt).toInt()
            this.dtbOffset = (info[19] as ULong).toLong()
        }
    }

    private fun get_recovery_dtbo_offset(): Long {
        return Helper.round_to_multiple(this.headerSize.toLong(), pageSize) +
                Helper.round_to_multiple(this.kernelLength, pageSize) +
                Helper.round_to_multiple(this.ramdiskLength, pageSize) +
                Helper.round_to_multiple(this.secondBootloaderLength, pageSize)
    }

    fun encode(): ByteArray {
        val pageSizeChoices: MutableSet<Long> = mutableSetOf<Long>().apply {
            (11..14).forEach { add(2.0.pow(it).toLong()) }
        }
        assert(pageSizeChoices.contains(pageSize.toLong())) { "invalid parameter [pageSize=$pageSize], (choose from $pageSizeChoices)" }
        return Struct3(FORMAT_STRING).pack(
                magic,
                //10I
                kernelLength,
                kernelOffset,
                ramdiskLength,
                ramdiskOffset,
                secondBootloaderLength,
                secondBootloaderOffset,
                tagsOffset,
                pageSize,
                headerVersion,
                (Common.packOsVersion(osVersion) shl 11) or Common.packOsPatchLevel(osPatchLevel),
                //16s
                board,
                //512s
                cmdline.substring(0, minOf(512, cmdline.length)),
                //32b
                hash!!,
                //1024s
                if (cmdline.length > 512) cmdline.substring(512) else "",
                //I
                recoveryDtboLength,
                //Q
                if (headerVersion > 0) recoveryDtboOffset else 0,
                //I
                when (headerVersion) {
                    0 -> BOOT_IMAGE_HEADER_V0_SIZE
                    1 -> BOOT_IMAGE_HEADER_V1_SIZE
                    2 -> BOOT_IMAGE_HEADER_V2_SIZE
                    else -> java.lang.IllegalArgumentException("headerVersion $headerVersion illegal")
                },
                //I
                dtbLength,
                //Q
                if (headerVersion > 1) dtbOffset else 0
        )
    }

    companion object {
        internal val log = LoggerFactory.getLogger(BootHeaderV2::class.java)
        const val magic = "ANDROID!"
        const val FORMAT_STRING = "8s" + //"ANDROID!"
                "10I" +
                "16s" +     //board name
                "512s" +    //cmdline part 1
                "32b" +     //hash digest
                "1024s" +   //cmdline part 2
                "I" +       //dtbo length [v1]
                "Q" +       //dtbo offset [v1]
                "I" +       //header size [v1]
                "I" +       //dtb length [v2]
                "Q"         //dtb offset [v2]
        const val BOOT_IMAGE_HEADER_V2_SIZE = 1660
        const val BOOT_IMAGE_HEADER_V1_SIZE = 1648
        const val BOOT_IMAGE_HEADER_V0_SIZE = 0

        init {
            assert(BOOT_IMAGE_HEADER_V2_SIZE == Struct3(FORMAT_STRING).calcSize())
        }

    }
}
