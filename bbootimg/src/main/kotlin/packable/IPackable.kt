package cfig.packable

interface IPackable {
    fun capabilities(): List<String> {
        return listOf("^dtbo\\.img$")
    }
    fun unpack(fileName: String = "dtbo.img")
    fun pack(fileName: String = "dtbo.img")
    fun flash(fileName: String = "dtbo.img") {

    }
}
