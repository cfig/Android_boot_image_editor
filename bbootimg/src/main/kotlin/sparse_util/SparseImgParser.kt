package cfig.sparse_util

import cfig.EnvironmentVerifier
import cfig.packable.IPackable
import org.slf4j.LoggerFactory
import cfig.helper.Helper.Companion.check_call
import java.io.FileInputStream
import java.io.File
import com.fasterxml.jackson.databind.ObjectMapper
import avb.blob.Footer
import cfig.Avb

@OptIn(ExperimentalUnsignedTypes::class)
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
