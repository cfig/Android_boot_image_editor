package cfig

data class ImgInfo(
        //kernel
        var kernelPosition: Int = 0,
        var kernelLength: Int = 0,
        //ramdisk
        var ramdiskPosition: Int = 0,
        var ramdiskLength: Int = 0,
        //second bootloader
        var secondBootloaderPosition: Int = 0,
        var secondBootloaderLength: Int = 0,
        //dtbo
        var recoveryDtboPosition: Int = 0,
        var recoveryDtboLength: Int = 0,

        var headerSize: Int = 0,
        var hash: ByteArray = ByteArray(0),

        //signature
        var signature: Any? = null
) {
    data class AvbSignature(
            var type: String = "avb",
            var originalImageSize: Int? = null,
            var imageSize: Int? = null,
            var partName: String? = null,
            var salt: String = "",
            var hashAlgorithm: String? = null,
            var algorithm: String? = null)

    data class VeritySignature(
            var type: String = "dm-verity",
            var path: String = "/boot",
            var verity_pk8: String = "security/verity.pk8",
            var verity_pem: String = "security/verity.x509.pem",
            var jarPath: String = "aosp/boot_signer/build/libs/boot_signer.jar")
}
