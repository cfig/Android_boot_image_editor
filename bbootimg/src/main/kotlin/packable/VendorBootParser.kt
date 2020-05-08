package cfig.packable

import cfig.Helper
import cfig.bootimg.v3.VendorBoot
import cfig.packable.BootImgParser.Companion.updateVbmeta
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
        updateVbmeta(fileName)
    }
}
