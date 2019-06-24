package cfig

import cfig.bootimg.BootImgInfo
import de.vandermeer.asciitable.AsciiTable
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.system.exitProcess

@ExperimentalUnsignedTypes
fun main(args: Array<String>) {
    val log = LoggerFactory.getLogger("Launcher")
    if ((args.size == 6) && args[0] in setOf("pack", "unpack", "sign")) {
        if (args[1] == "vbmeta.img") {
            when (args[0]) {
                "unpack" -> {
                    if (File(UnifiedConfig.workDir).exists()) File(UnifiedConfig.workDir).deleteRecursively()
                    File(UnifiedConfig.workDir).mkdirs()
                    Avb().parseVbMeta(args[1])
                }
                "pack" -> {
                    Avb().packVbMetaWithPadding()
                }
                "sign" -> {
                    log.info("vbmeta is already signed")
                }
            }
        } else {
            when (args[0]) {
                "unpack" -> {
                    if (File(UnifiedConfig.workDir).exists()) File(UnifiedConfig.workDir).deleteRecursively()
                    File(UnifiedConfig.workDir).mkdirs()
                    val info = Parser().parseBootImgHeader(fileName = args[1], avbtool = args[3])
                    InfoTable.instance.addRule()
                    InfoTable.instance.addRow("image info", ParamConfig().cfg)
                    if (info.signatureType == BootImgInfo.VerifyType.AVB) {
                        log.info("continue to analyze vbmeta info in " + args[1])
                        Avb().parseVbMeta(args[1])
                        InfoTable.instance.addRule()
                        InfoTable.instance.addRow("AVB info", Avb.getJsonFileName(args[1]))
                        if (File("vbmeta.img").exists()) {
                            Avb().parseVbMeta("vbmeta.img")
                        }
                    }
                    Parser().extractBootImg(fileName = args[1], info2 = info)

                    InfoTable.instance.addRule()
                    val tableHeader = AsciiTable().apply {
                        addRule()
                        addRow("What", "Where")
                        addRule()
                    }
                    log.info("\n\t\t\tUnpack Summary of ${args[1]}\n{}\n{}", tableHeader.render(), InfoTable.instance.render())
                    log.info("Following components are not present: ${InfoTable.missingParts}")
                }
                "pack" -> {
                    Packer().pack(mkbootfsBin = args[5])
                }
                "sign" -> {
                    Signer.sign(avbtool = args[3], bootSigner = args[4])
                    val readBack2 = UnifiedConfig.readBack2()
                    if (readBack2.signatureType == BootImgInfo.VerifyType.AVB) {
                        if (File("vbmeta.img").exists()) {
//                            val sig = readBack[2] as ImgInfo.AvbSignature
//                            val newBootImgInfo = Avb().parseVbMeta(args[1] + ".signed")
//                            val hashDesc = newBootImgInfo.auxBlob!!.hashDescriptors[0]
//                            val origVbMeta = ObjectMapper().readValue(File(Avb.getJsonFileName("vbmeta.img")),
//                                    AVBInfo::class.java)
//                            for (i in 0..(origVbMeta.auxBlob!!.hashDescriptors.size - 1)) {
//                                if (origVbMeta.auxBlob!!.hashDescriptors[i].partition_name == sig.partName) {
//                                    val seq = origVbMeta.auxBlob!!.hashDescriptors[i].sequence
//                                    origVbMeta.auxBlob!!.hashDescriptors[i] = hashDesc
//                                    origVbMeta.auxBlob!!.hashDescriptors[i].sequence = seq
//                                }
//                            }
//                            ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(File(Avb.getJsonFileName("vbmeta.img")), origVbMeta)
//                            log.info("vbmeta.img info updated")
//                            Avb().packVbMetaWithPadding()
                        } else {
                            log.info("no vbmeta.img need to update")
                        }
                    }//end-of-avb
                }//end-of-sign
            }
        }
    } else {
        println("Usage: unpack <boot_image_path> <mkbootimg_bin_path> <avbtool_path> <boot_signer_path> <mkbootfs_bin_path>")
        println("Usage:  pack  <boot_image_path> <mkbootimg_bin_path> <avbtool_path> <boot_signer_path> <mkbootfs_bin_path>")
        println("Usage:  sign  <boot_image_path> <mkbootimg_bin_path> <avbtool_path> <boot_signer_path> <mkbootfs_bin_path>")
        exitProcess(1)
    }
}

/*
    (a * x) mod m == 1
 */
//    fun modInv(a: Int, m: Int): Int {
//        for (x in 0 until m) {
//            if (a * x % m == 1) {
//                return x
//            }
//        }
//        throw IllegalArgumentException("modular multiplicative inverse of [$a] under modulo [$m] doesn't exist")
//    }
//
