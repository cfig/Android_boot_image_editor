package cfig

fun main(args: Array<String>) {
    if ((args.size == 5) && args[0] in setOf("pack", "unpack", "sign")) {
        when (args[0]) {
            "unpack" -> {
                Parser().parseAndExtract(args[1], args[3])
            }
            "pack" -> {
                Packer().pack(args[2])
            }
            "sign" -> {
                Packer().sign(args[3], args[4])
            }
        }
    } else {
        println("Usage: unpack <boot_image_path> <mkbootfs_bin_path> <avbtool_path> <boot_signer_path>")
        println("Usage:  pack  <boot_image_path> <mkbootfs_bin_path> <avbtool_path> <boot_signer_path>")
        println("Usage:  sign  <boot_image_path> <mkbootfs_bin_path> <avbtool_path> <boot_signer_path>")
        System.exit(1)
    }
}
