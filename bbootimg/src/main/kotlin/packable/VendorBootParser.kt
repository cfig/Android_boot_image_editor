package cfig.packable

import cfig.Avb
import cfig.helper.Helper
import cfig.bootimg.v3.VendorBoot
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import java.io.File

class VendorBootParser : IPackable {
    override val loopNo: Int = 0
    private val log = LoggerFactory.getLogger(VendorBootParser::class.java)
    private val workDir = Helper.prop("workDir")
    override fun capabilities(): List<String> {
        return listOf("^vendor_boot\\.img$")
    }

    override fun unpack(fileName: String) {
        cleanUp()
        val vb = VendorBoot
                .parse(fileName)
                .extractImages()
                .extractVBMeta()
                .printSummary()
        log.debug(vb.toString())
    }

    override fun pack(fileName: String) {
        val cfgFile = "$workDir/${fileName.removeSuffix(".img")}.json"
        log.info("Loading config from $cfgFile")
        ObjectMapper().readValue(File(cfgFile), VendorBoot::class.java)
                .pack()
                .sign()
        Avb.updateVbmeta(fileName)
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
