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

package rom.sparse

import avb.blob.Footer
import cfig.helper.Helper
import cfig.packable.IPackable
import cfig.packable.VBMetaParser
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import kotlin.io.path.Path
import kotlin.io.path.deleteIfExists

class SparseImgParser : IPackable {
    override val loopNo: Int
        get() = 0
    private val log = LoggerFactory.getLogger(SparseImgParser::class.java)

    override fun capabilities(): List<String> {
        return listOf(
            "^(system|system_ext|system_other|system_dlkm)\\.img$",
            "^(vendor|vendor_dlkm|product|cache|userdata|super|oem|odm|odm_dlkm)\\.img$"
        )
    }

    override fun unpack(fileName: String) {
        clear()
        SparseImage
            .parse(fileName)
            .printSummary(fileName)
    }

    override fun pack(fileName: String) {
        //TODO("not implemented: refer to https://github.com/cfig/Android_boot_image_editor/issues/133")
        val cfgFile = outDir + fileName.removeSuffix(".img") + ".json"
        val readBackSi = ObjectMapper().readValue(File(cfgFile), SparseImage::class.java)
        readBackSi
            .pack()
            .updateVbmeta()
            .unwrap()
    }

    // invoked solely by reflection
    fun `@footer`(fileName: String) {
        FileInputStream(fileName).use { fis ->
            fis.skip(File(fileName).length() - Footer.SIZE)
            try {
                val footer = Footer(fis)
                log.info("\n" + ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(footer))
                //val vbmetaData =
                //    Dumpling(Dumpling(fileName).readFully(Pair(footer.vbMetaOffset, footer.vbMetaSize.toInt())))
                //val ai = AVBInfo.parseFrom(vbmetaData)
                //log.warn("XX")
                //ai.dumpDefault("temp")
            } catch (e: IllegalArgumentException) {
                log.info("image $fileName has no AVB Footer")
            }
        }
    }

    override fun `@verify`(fileName: String) {
        super.`@verify`(fileName)
    }

    override fun flash(fileName: String, deviceName: String) {
        TODO("not implemented")
    }

    fun clear(fileName: String) {
        super.clear()
        listOf("", ".clear", ".signed").forEach {
            Path("$fileName$it").deleteIfExists()
        }
        VBMetaParser().clear("vbmeta.img")
    }


    companion object {
        private val workDir = Helper.prop("workDir")
        private var outerFsType = "raw"
        private var innerFsType = "raw"
    }
}
