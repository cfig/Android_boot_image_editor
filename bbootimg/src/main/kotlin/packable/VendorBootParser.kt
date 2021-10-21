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

package cfig.packable

import cfig.bootimg.v3.VendorBoot
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import java.io.File

class VendorBootParser : IPackable {
    override val loopNo: Int = 0
    private val log = LoggerFactory.getLogger(VendorBootParser::class.java)
    override fun capabilities(): List<String> {
        return listOf("^vendor_boot(-debug)?\\.img$")
    }

    override fun unpack(fileName: String) {
        cleanUp()
        val vb = VendorBoot
            .parse(fileName)
            .extractImages()
            .extractVBMeta()
            .printUnpackSummary()
        log.debug(vb.toString())
    }

    override fun pack(fileName: String) {
        val cfgFile = "$outDir/${fileName.removeSuffix(".img")}.json"
        log.info("Loading config from $cfgFile")
        ObjectMapper().readValue(File(cfgFile), VendorBoot::class.java)
            .pack()
            .sign()
            .updateVbmeta()
            .printPackSummary()
    }

    override fun `@verify`(fileName: String) {
        super.`@verify`(fileName)
    }

    override fun pull(fileName: String, deviceName: String) {
        super.pull(fileName, deviceName)
    }

    override fun flash(fileName: String, deviceName: String) {
        val stem = fileName.substring(0, fileName.indexOf("."))
        super.flash("$fileName.signed", stem)

        if (File("vbmeta.img.signed").exists()) {
            super.flash("vbmeta.img.signed", "vbmeta")
        }
    }
}
