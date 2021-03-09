package cfig.packable

import avb.AVBInfo
import avb.blob.Footer
import cfig.Avb
import cfig.helper.Helper
import cfig.bootimg.Common.Companion.probeHeaderVersion
import cfig.bootimg.v2.BootV2
import cfig.bootimg.v3.BootV3
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream

@OptIn(ExperimentalUnsignedTypes::class)
class BootImgParser() : IPackable {
    override val loopNo: Int
        get() = 0
    private val workDir = Helper.prop("workDir")

    override fun capabilities(): List<String> {
        return listOf("^boot\\.img$", "^recovery\\.img$", "^recovery-two-step\\.img$")
    }

    override fun unpack(fileName: String) {
        cleanUp()
        try {
            val hv = probeHeaderVersion(fileName)
            log.info("header version $hv")
            if (hv == 3) {
                val b3 = BootV3
                    .parse(fileName)
                    .extractImages()
                    .extractVBMeta()
                    .printSummary()
                log.debug(b3.toString())
                return
            } else {
                val b2 = BootV2
                    .parse(fileName)
                    .extractImages()
                    .extractVBMeta()
                    .printSummary()
                log.debug(b2.toString())
            }
        } catch (e: IllegalArgumentException) {
            log.error(e.message)
            log.error("Parser can not continue")
        }
    }

    override fun pack(fileName: String) {
        val cfgFile = workDir + fileName.removeSuffix(".img") + ".json"
        log.info("Loading config from $cfgFile")
        if (3 == probeHeaderVersion(fileName)) {
            ObjectMapper().readValue(File(cfgFile), BootV3::class.java)
                .pack()
                .sign(fileName)
        } else {
            ObjectMapper().readValue(File(cfgFile), BootV2::class.java)
                .pack()
                .sign()
        }
        Avb.updateVbmeta(fileName)
    }

    override fun flash(fileName: String, deviceName: String) {
        val stem = fileName.substring(0, fileName.indexOf("."))
        super.flash("$fileName.signed", stem)

        if (File("vbmeta.img.signed").exists()) {
            super.flash("vbmeta.img.signed", "vbmeta")
        }
    }

    // invoked solely by reflection
    fun `@footer`(image_file: String) {
        FileInputStream(image_file).use { fis ->
            fis.skip(File(image_file).length() - Footer.SIZE)
            try {
                val footer = Footer(fis)
                log.info("\n" + ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(footer))
            } catch (e: IllegalArgumentException) {
                log.info("image $image_file has no AVB Footer")
            }
        }
    }

    override fun `@verify`(fileName: String) {
        super.`@verify`(fileName)
    }

    override fun pull(fileName: String, deviceName: String) {
        super.pull(fileName, deviceName)
    }

    companion object {
        private val log = LoggerFactory.getLogger(BootImgParser::class.java)
    }
}
