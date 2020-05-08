package cfig.packable

import avb.AVBInfo
import avb.blob.Footer
import cfig.Avb
import cfig.Helper
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
            updateVbmeta(fileName)
        } else {
            ObjectMapper().readValue(File(cfgFile), BootV2::class.java)
                    .pack()
                    .sign()
            updateVbmeta(fileName)
        }
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

    companion object {
        private val log = LoggerFactory.getLogger(BootImgParser::class.java)

        fun updateVbmeta(fileName: String) {
            log.info("Updating vbmeta.img side by side ...")
            if (File("vbmeta.img").exists()) {
                val partitionName = ObjectMapper().readValue(File(Avb.getJsonFileName(fileName)), AVBInfo::class.java).let {
                    it.auxBlob!!.hashDescriptors.get(0).partition_name
                }
                val newHashDesc = Avb().parseVbMeta("$fileName.signed", dumpFile = false)
                assert(newHashDesc.auxBlob!!.hashDescriptors.size == 1)
                val mainVBMeta = ObjectMapper().readValue(File(Avb.getJsonFileName("vbmeta.img")), AVBInfo::class.java).apply {
                    val itr = this.auxBlob!!.hashDescriptors.iterator()
                    var seq = 0
                    while (itr.hasNext()) {
                        val itrValue = itr.next()
                        if (itrValue.partition_name == partitionName) {
                            log.info("Found $partitionName in vbmeta, update it")
                            seq = itrValue.sequence
                            itr.remove()
                            break
                        }
                    }
                    val hd = newHashDesc.auxBlob!!.hashDescriptors.get(0).apply { this.sequence = seq }
                    this.auxBlob!!.hashDescriptors.add(hd)
                }
                Avb().packVbMetaWithPadding("vbmeta.img", mainVBMeta)
            }
        }
    }
}
