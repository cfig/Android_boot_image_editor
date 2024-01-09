// Copyright 2022 yuyezhong@gmail.com
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

package cfig.packable

import cc.cfig.droid.ota.Payload
import org.slf4j.LoggerFactory
import kotlin.io.path.Path
import kotlin.io.path.deleteIfExists

class PayloadBinParser : IPackable {
    override val loopNo: Int = 0
    private val log = LoggerFactory.getLogger(PayloadBinParser::class.java)
    override fun capabilities(): List<String> {
        return listOf("^payload\\.bin$")
    }

    override fun unpack(fileName: String) {
        clear()
        Payload.parse(fileName).let { pl ->
            pl.setUp()
            pl.printInfo()
            pl.unpack()
        }
    }

    override fun pack(fileName: String) {
    }

    override fun `@verify`(fileName: String) {
        super.`@verify`(fileName)
    }

    override fun pull(fileName: String, deviceName: String) {
        super.pull(fileName, deviceName)
    }

    fun clear(fileName: String) {
        super.clear()
        Path(fileName).deleteIfExists()
    }

    override fun flash(fileName: String, deviceName: String) {
    }
}
