package cfig.packable

import cfig.*
import cfig.bootimg.BootImgInfo
import de.vandermeer.asciitable.AsciiTable
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.IllegalArgumentException

@ExperimentalUnsignedTypes
class BootImgParser : IPackable {
    override fun flash(fileName: String) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    private val log = LoggerFactory.getLogger(BootImgParser::class.java)

    override fun capabilities(): List<String> {
        return listOf("^boot\\.img$", "^recovery\\.img$", "^recovery-two-step\\.img$")
    }

    override fun unpack(fileName: String) {
        if (File(UnifiedConfig.workDir).exists()) File(UnifiedConfig.workDir).deleteRecursively()
        File(UnifiedConfig.workDir).mkdirs()
        try {
            val info = Parser().parseBootImgHeader(fileName, avbtool = "avb/avbtool")
            InfoTable.instance.addRule()
            InfoTable.instance.addRow("image info", ParamConfig().cfg)
            if (info.signatureType == BootImgInfo.VerifyType.AVB) {
                log.info("continue to analyze vbmeta info in $fileName")
                Avb().parseVbMeta(fileName)
                InfoTable.instance.addRule()
                InfoTable.instance.addRow("AVB info", Avb.getJsonFileName(fileName))
            }
            Parser().extractBootImg(fileName, info2 = info)

            InfoTable.instance.addRule()
            val tableHeader = AsciiTable().apply {
                addRule()
                addRow("What", "Where")
                addRule()
            }
            log.info("\n\t\t\tUnpack Summary of $fileName\n{}\n{}", tableHeader.render(), InfoTable.instance.render())
            log.info("Following components are not present: ${InfoTable.missingParts}")
        } catch (e: IllegalArgumentException) {
            log.error(e.message)
            log.error("Parser can not continue")
        }
    }

    override fun pack(fileName: String) {
        Packer().pack(mkbootfsBin = "mkbootfs/build/exe/mkbootfs/mkbootfs")
        Signer.sign(avbtool = "avb/avbtool", bootSigner = "boot_signer/build/libs/boot_signer.jar")
    }
}