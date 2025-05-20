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
import cfig.bootimg.Common
import cfig.helper.Dumpling
import cfig.helper.Helper
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import kotlin.io.path.Path
import kotlin.io.path.deleteIfExists

class VBMetaParser : IPackable {
    override val loopNo: Int
        get() = 1

    override fun capabilities(): List<String> {
        return listOf("^vbmeta\\.img$", "^vbmeta\\_[a-z]+.img$")
    }

    // lazy mode
    override fun unpack(fileName: String) {
        unpackInternal(fileName, Helper.prop("workDir")!!)
    }

    // manual mode
    fun unpackInternal(inFileName: String, unpackDir: String) {
        //common
        log.info("unpackInternal(fileName: $inFileName, unpackDir: $unpackDir)")
        val fileName = File(inFileName).canonicalPath
        Helper.setProp("workDir", File(unpackDir).canonicalPath)
        //prepare workdir
        File(Helper.prop("workDir")!!).let {
            if (!it.exists()) {
                it.mkdirs()
            }
        }
        //workspace.ini
        log.info("workspace set to $unpackDir")
        Common.createWorkspaceIni(fileName, prefix = "vbmeta")

        val ai = AVBInfo.parseFrom(Dumpling(fileName)).dumpDefault(fileName)
        log.info("Signing Key: " + Avb.inspectKey(ai))
    }

    override fun pack(fileName: String) {
        packInternal(outDir, fileName)
    }

    // called via reflection
    fun packInternal(workspace: String, outFileName: String) {
        log.info("packInternal(workspace: $workspace, $outFileName)")
        Helper.setProp("workDir", workspace)
        val iniRole = Common.loadProperties(File(workspace, "workspace.ini").canonicalPath).getProperty("vbmeta.role")
        val blob = ObjectMapper().readValue(File(Avb.getJsonFileName(iniRole)), AVBInfo::class.java).encodePadded()
        log.info("Writing padded vbmeta to file: $outFileName.signed")
        Files.write(Paths.get("$outFileName.signed"), blob, StandardOpenOption.CREATE)
    }

    override fun flash(fileName: String, deviceName: String) {
        val stem = fileName.substring(0, fileName.indexOf("."))
        super.flash("$fileName.signed", stem)
    }

    override fun `@verify`(fileName: String) {
        super.`@verify`(fileName)
    }

    fun pull(fileName: String) {
        super.pull(fileName, File(fileName).nameWithoutExtension)
    }

    fun clear(fileName: String) {
        super.clear()
        listOf("", ".signed").forEach {
            Path("$fileName$it").deleteIfExists()
        }
    }

    private val log = LoggerFactory.getLogger(VBMetaParser::class.java)
}
