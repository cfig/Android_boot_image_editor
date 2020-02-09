package cfig.packable

import cfig.Avb
import cfig.UnifiedConfig
import java.io.File

@ExperimentalUnsignedTypes
class VBMetaParser: IPackable {
    override val loopNo: Int
        get() = 1

    override fun capabilities(): List<String> {
        return listOf("^vbmeta\\.img$", "^vbmeta\\_[a-z]+.img$")
    }

    override fun cleanUp() {
        File(UnifiedConfig.workDir).mkdirs()
    }

    override fun unpack(fileName: String) {
        cleanUp()
        Avb().parseVbMeta(fileName)
    }

    override fun pack(fileName: String) {
        Avb().packVbMetaWithPadding(fileName)
    }

    override fun flash(fileName: String, deviceName: String) {
        val stem = fileName.substring(0, fileName.indexOf("."))
        super.flash("$fileName.signed", stem)
    }
}
