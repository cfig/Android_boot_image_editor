// Copyright 2022-2024 yuyezhong@gmail.com
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

import cfig.helper.Helper
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import rom.misc.MiscImage
import java.io.File
import java.io.RandomAccessFile
import kotlin.io.nameWithoutExtension
import kotlin.io.path.Path
import kotlin.io.path.deleteIfExists

class MiscImgParser : IPackable {
    override val loopNo: Int
        get() = 0

    override fun capabilities(): List<String> {
        return listOf("^misc\\.img$")
    }

    private fun getOutputFile(fileName: String): File {
        return File(workDir + "/" + File(fileName).name.removeSuffix(".img") + ".json")
    }

    override fun unpack(fileName: String) {
        clear()
        val misc = MiscImage.parse(fileName)
        log.info(misc.toString())
        ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(getOutputFile(fileName), misc)
        ObjectMapper().writerWithDefaultPrettyPrinter()
            .writeValue(getOutputFile("sample.img"), MiscImage.BootloaderMessage.generateSamples())
        log.info(getOutputFile(fileName).path + " is ready")
    }

    override fun pack(fileName: String) {
        val misc = ObjectMapper().readValue(getOutputFile(fileName), MiscImage::class.java)
        val out = File("$fileName.new")
        File(fileName).copyTo(out, true)
        RandomAccessFile(out.name, "rw").use { raf ->
            raf.write(misc.bcb.encode())
            raf.seek(MiscImage.MiscBootControl.OFFSET)
            if (misc.mbcb != null) {
                raf.write(misc.mbcb!!.encode())
            }
            raf.seek(MiscImage.VirtualABMessage.OFFSET)
            if (misc.virtualAB != null) {
                raf.write(misc.virtualAB!!.encode())
            }
        }
        log.info("${out.name} is ready")
    }

    fun flash(fileName: String) {
        super.flash("$fileName.new", File(fileName).nameWithoutExtension)
    }

    override fun `@verify`(fileName: String) {
        super.`@verify`(fileName)
    }

    fun pull(fileName: String) {
        super.pull(fileName, File(fileName).nameWithoutExtension)
    }

    fun clear(fileName: String) {
        super.clear()
        listOf("", ".new").forEach {
            Path("$fileName$it").deleteIfExists()
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(MiscImgParser::class.java)
        private val workDir = Helper.prop("workDir")
    }
}
