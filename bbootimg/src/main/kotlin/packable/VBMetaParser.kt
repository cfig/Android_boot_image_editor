package cfig.packable

import cfig.Avb
import cfig.Helper
import java.io.File

@OptIn(ExperimentalUnsignedTypes::class)
class VBMetaParser: IPackable {
    override val loopNo: Int
        get() = 1

    override fun capabilities(): List<String> {
        return listOf("^vbmeta\\.img$", "^vbmeta\\_[a-z]+.img$")
    }

    override fun cleanUp() {
        File(Helper.prop("workDir")).mkdirs()
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

    override fun pull(fileName: String, deviceName: String) {
        super.pull(fileName, deviceName)
    }
}
