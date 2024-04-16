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

import cfig.bootimg.Common
import cfig.bootimg.v3.VendorBoot
import cfig.helper.Helper
import cfig.helper.Helper.Companion.check_call
import cfig.helper.ZipHelper
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.deleteIfExists

class OTAzipParser : IPackable {
    override val loopNo: Int
        get() = 0

    override fun capabilities(): List<String> {
        return listOf("^ota\\.zip$")
    }

    private fun getOutputFile(fileName: String): File {
        return File(workDir + "/" + File(fileName).name.removeSuffix(".zip") + ".json")
    }

    override fun unpack(fileName: String) {
        clear()
        ZipHelper.unzip(fileName, outDir)
        printUnpackSummary(fileName)
    }

    private fun printUnpackSummary(fileName: String)  {
        val prints: MutableList<Pair<String, String>> = mutableListOf()
        prints.add(Pair("file", fileName))
        prints.add(Pair("unpack directory", workDir))
        log.info("\n" + Common.table2String(prints))
    }

    override fun pack(fileName: String) {
        Path("clear.zip").deleteIfExists()
        Path("new_ota.zip").deleteIfExists()
        log.info("(1/2) Zipping ...")
        "zip -r ../../clear.zip .".check_call(outDir)
        log.info("(2/2) Signing ...")
        val apksigner = "aosp/apksigner/build/libs/apksigner-1.0.jar"
        ("java -Xmx2G -ea " +
                "-jar $apksigner " +
                "-w aosp/security/testkey.x509.pem " +
                " aosp/security/testkey.pk8 " +
                " clear.zip " +
                " new_ota.zip").check_call()
        printPackSummary(fileName)
    }

    private fun printPackSummary(fileName: String) {
        val prints: MutableList<Pair<String, String>> = mutableListOf()
        prints.add(Pair("signed OTA", "new_ota.zip"))
        log.info("\n" + Common.table2String(prints))
    }

    fun clear(fileName: String) {
        super.clear()
        listOf(fileName, "new_ota.zip", "clear.zip").forEach {
            Path(it).deleteIfExists()
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(OTAzipParser::class.java)
        private val workDir = Helper.prop("workDir")!!
    }
}
