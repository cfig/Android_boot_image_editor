package cfig

fun main(args: Array<String>) {
    if ((args.size == 6) && args[0] in setOf("pack", "unpack", "sign")) {
        when (args[0]) {
            "unpack" -> {
                Parser().parseAndExtract(fileName = args[1], avbtool = args[3])
            }
            "pack" -> {
                Packer().pack(mkbootimgBin = args[2], mkbootfsBin = args[5])
            }
            "sign" -> {
                Packer().sign(avbtool = args[3], bootSigner = args[4])
            }
        }
    } else {
        println("Usage: unpack <boot_image_path> <mkbootimg_bin_path> <avbtool_path> <boot_signer_path> <mkbootfs_bin_path>")
        println("Usage:  pack  <boot_image_path> <mkbootimg_bin_path> <avbtool_path> <boot_signer_path> <mkbootfs_bin_path>")
        println("Usage:  sign  <boot_image_path> <mkbootimg_bin_path> <avbtool_path> <boot_signer_path> <mkbootfs_bin_path>")
        System.exit(1)
    }
}
