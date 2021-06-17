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

package cfig.bootloader_message

import cfig.io.Struct3
import org.slf4j.LoggerFactory
import java.io.FileInputStream

class BootloaderMsgAB( //offset 2k, size 2k
        var slotSuffix: String = "",
        var updateChannel: String = "",
        var reserved: ByteArray = byteArrayOf()
) {
    companion object {
        private const val FORMAT_STRING = "32s128s1888b"
        const val SIZE = 2048
        private val log = LoggerFactory.getLogger(BootloaderMsgAB::class.java.simpleName)

        init {
            assert(SIZE == Struct3(FORMAT_STRING).calcSize())
        }
    }

    constructor(fis: FileInputStream) : this() {
        val info = Struct3(FORMAT_STRING).unpack(fis)
        this.slotSuffix = info[0] as String
        this.updateChannel = info[1] as String
        this.reserved = info[2] as ByteArray
    }

    fun encode(): ByteArray {
        return Struct3(FORMAT_STRING).pack(
                this.slotSuffix,
                this.updateChannel,
                byteArrayOf())
    }
}
