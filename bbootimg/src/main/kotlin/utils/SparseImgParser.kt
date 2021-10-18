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

package cfig.utils

import cfig.packable.IPackable
import org.slf4j.LoggerFactory
import cfig.helper.Helper.Companion.check_call
import java.io.FileInputStream
import java.io.File
import com.fasterxml.jackson.databind.ObjectMapper
import avb.blob.Footer

class SparseImgParser : IPackable {
    override val loopNo: Int
        get() = 0
    private val log = LoggerFactory.getLogger(SparseImgParser::class.java)
    private val simg2imgBin: String
    private val img2simgBin: String

    init {
        val osSuffix = if (EnvironmentVerifier().isMacOS) "macos" else "linux"
        simg2imgBin = "./aosp/libsparse/simg2img/build/install/main/release/$osSuffix/simg2img"
        img2simgBin = "./aosp/libsparse/img2simg/build/install/main/release/$osSuffix/img2simg"
    }

    override fun capabilities(): List<String> {
        return listOf("^(system|system_ext|system_other|vendor|product|cache|userdata|super)\\.img$")
    }

    override fun unpack(fileName: String) {
        cleanUp()
        simg2img(fileName, "$fileName.unsparse")
    }

    override fun pack(fileName: String) {
        img2simg("$fileName.unsparse", "$fileName.new")
    }

    // invoked solely by reflection
    fun `@footer`(fileName: String) {
        FileInputStream(fileName).use { fis ->
            fis.skip(File(fileName).length() - Footer.SIZE)
            try {
                val footer = Footer(fis)
                log.info("\n" + ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(footer))
            } catch (e: IllegalArgumentException) {
                log.info("image $fileName has no AVB Footer")
            }
        }
    }

    override fun `@verify`(fileName: String) {
        super.`@verify`(fileName)
    }

    private fun simg2img(sparseIn: String, flatOut: String) {
        log.info("parsing Android sparse image $sparseIn ...")
        "$simg2imgBin $sparseIn $flatOut".check_call()
        "file $sparseIn".check_call()
        "file $flatOut".check_call()
        log.info("parsed Android sparse image $sparseIn -> $flatOut")
    }

    private fun img2simg(flatIn: String, sparseOut: String) {
        log.info("transforming image to Android sparse format: $flatIn ...")
        "$img2simgBin $flatIn $sparseOut".check_call()
        "file $flatIn".check_call()
        "file $sparseOut".check_call()
        log.info("transformed Android sparse image: $flatIn -> $sparseOut")
    }

    override fun flash(fileName: String, deviceName: String) {
        TODO("not implemented")
    }
}
