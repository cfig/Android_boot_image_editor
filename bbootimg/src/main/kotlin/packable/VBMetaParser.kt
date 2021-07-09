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

import avb.AVBInfo
import cfig.Avb
import cfig.helper.Helper
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

class VBMetaParser: IPackable {
    override val loopNo: Int
        get() = 1

    override fun capabilities(): List<String> {
        return listOf("^vbmeta\\.img$", "^vbmeta\\_[a-z]+.img$")
    }

    override fun cleanUp() {
        File(Helper.prop("workDir")).mkdirs()
    }

    override fun unpack(fileName: String) {
        cleanUp()
        AVBInfo.parseFrom(fileName).dumpDefault(fileName)
    }

    override fun pack(fileName: String) {
        val blob = ObjectMapper().readValue(File(Avb.getJsonFileName(fileName)), AVBInfo::class.java).encodePadded()
        log.info("Writing padded vbmeta to file: $fileName.signed")
        Files.write(Paths.get("$fileName.signed"), blob, StandardOpenOption.CREATE)
    }

    override fun flash(fileName: String, deviceName: String) {
        val stem = fileName.substring(0, fileName.indexOf("."))
        super.flash("$fileName.signed", stem)
    }

    override fun `@verify`(fileName: String) {
        super.`@verify`(fileName)
    }

    override fun pull(fileName: String, deviceName: String) {
        super.pull(fileName, deviceName)
    }

    private val log = LoggerFactory.getLogger(VBMetaParser::class.java)
}
