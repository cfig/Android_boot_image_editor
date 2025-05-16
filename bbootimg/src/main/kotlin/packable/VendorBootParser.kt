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

import cfig.bootimg.Common
import cfig.bootimg.v3.VendorBoot
import cfig.helper.Helper
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.deleteIfExists

class VendorBootParser : IPackable {
    override val loopNo: Int = 0
    private val log = LoggerFactory.getLogger(VendorBootParser::class.java)
    override fun capabilities(): List<String> {
        return listOf("^vendor_boot(-debug)?\\.img$", "^vendor_kernel_boot\\.img$")
    }

    override fun unpack(fileName: String) {
        log.info("unpack(fileName: $fileName)")
        unpackInternal(fileName, Helper.prop("workDir")!!)
    }

    fun unpackInternal(fileName: String, unpackDir: String) {
        log.info("unpackInternal(fileName: $fileName, unpackDir: $unpackDir)")
        Helper.setProp("workDir", unpackDir)
        clear()
        //create workspace file
        Common.createWorkspaceIni(fileName)
        //create workspace file done
        val vb = VendorBoot
            .parse(fileName)
            .extractImages()
            .extractVBMeta()
            .printUnpackSummary()
        log.debug(vb.toString())
    }

    override fun pack(fileName: String) {
        log.info("packInternal($fileName)")
        packInternal(Helper.prop("workDir")!!, fileName)
    }

    fun packInternal(workspace: String, outFileName: String) {
        log.info("packInternal($workspace, $outFileName)")
        Helper.setProp("workDir", workspace)
        //intermediate
        Helper.joinPath(workspace, "intermediate").also { intermediateDir ->
            File(intermediateDir).let {
                if (!it.exists()) {
                    it.mkdir()
                }
            }
            Helper.setProp("intermediateDir", intermediateDir)
        }
        //workspace+cfg
        val iniRole = Common.loadProperties(File(workspace, "workspace.ini").canonicalPath).getProperty("role")
        val cfgFile = File(workspace, iniRole.removeSuffix(".img") + ".json").canonicalPath
        log.info("Loading config from $cfgFile")
        ObjectMapper().readValue(File(cfgFile), VendorBoot::class.java)
            .pack()
            .sign()
            .postCopy(outFileName)
            .updateVbmeta()
            .printPackSummary(outFileName)
    }

    override fun `@verify`(fileName: String) {
        super.`@verify`(fileName)
    }

    fun pull(fileName: String) {
        super.pull(fileName, File(fileName).nameWithoutExtension)
        super.pull("vbmeta.img", "vbmeta")
    }

    fun clear(fileName: String) {
        super.clear()
        listOf("", ".clear", ".google", ".clear", ".signed", ".signed2").forEach {
            Path("$fileName$it").deleteIfExists()
        }
        VBMetaParser().clear("vbmeta.img")
    }

    fun flash(fileName: String) {
        val stem = fileName.substring(0, fileName.indexOf("."))
        super.flash("$fileName.signed", stem)

        if (File("vbmeta.img.signed").exists()) {
            super.flash("vbmeta.img.signed", "vbmeta")
        }
    }
}
