package cfig

import avb.AVBInfo
import avb.alg.Algorithms
import cfig.bootimg.BootImgInfo
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.slf4j.LoggerFactory
import java.io.File

class Signer {
    companion object {
        private val log = LoggerFactory.getLogger(Signer::class.java)

        fun sign(avbtool: String, bootSigner: String) {
            log.info("Loading config from ${ParamConfig().cfg}")
            val info2 = UnifiedConfig.readBack2()
            val cfg = ObjectMapper().readValue(File(ParamConfig().cfg), UnifiedConfig::class.java)

            when (info2.signatureType) {
                BootImgInfo.VerifyType.VERIFY -> {
                    log.info("Signing with verified-boot 1.0 style")
                    val sig = ImgInfo.VeritySignature()
                    val bootSignCmd = "java -jar $bootSigner " +
                            "${sig.path} ${cfg.info.output}.clear " +
                            "${sig.verity_pk8} ${sig.verity_pem} " +
                            "${cfg.info.output}.signed"
                    log.info(bootSignCmd)
                    DefaultExecutor().execute(CommandLine.parse(bootSignCmd))
                }
                BootImgInfo.VerifyType.AVB -> {
                    log.info("Adding hash_footer with verified-boot 2.0 style")
                    val ai = ObjectMapper().readValue(File(Avb.getJsonFileName(cfg.info.output)), AVBInfo::class.java)
                    val alg = Algorithms.get(ai.header!!.algorithm_type.toInt())
                    val bootDesc = ai.auxBlob!!.hashDescriptors[0]

                    //our signer
                    File(cfg.info.output + ".clear").copyTo(File(cfg.info.output + ".signed"))
                    Avb().add_hash_footer(cfg.info.output + ".signed",
                            info2.imageSize.toLong(),
                            false,
                            false,
                            salt = Helper.toHexString(bootDesc.salt),
                            hash_algorithm = bootDesc.hash_algorithm_str,
                            partition_name = bootDesc.partition_name,
                            rollback_index = ai.header!!.rollback_index.toLong(),
                            common_algorithm = alg!!.name,
                            inReleaseString = ai.header!!.release_string)
                    //original signer
                    File(cfg.info.output + ".clear").copyTo(File(cfg.info.output + ".signed2"))
                    var cmdlineStr = "$avbtool add_hash_footer " +
                            "--image ${cfg.info.output}.signed2 " +
                            "--partition_size ${info2.imageSize} " +
                            "--salt ${Helper.toHexString(bootDesc.salt)} " +
                            "--partition_name ${bootDesc.partition_name} " +
                            "--hash_algorithm ${bootDesc.hash_algorithm_str} " +
                            "--algorithm ${alg.name} "
                    if (alg.defaultKey.isNotBlank()) {
                        cmdlineStr += "--key ${alg.defaultKey}"
                    }
                    log.warn(cmdlineStr)
                    val cmdLine = CommandLine.parse(cmdlineStr)
                    cmdLine.addArgument("--internal_release_string")
                    cmdLine.addArgument(ai.header!!.release_string, false)
                    DefaultExecutor().execute(cmdLine)
                    Parser.verifyAVBIntegrity(cfg.info.output, avbtool)
                }
            }
        }

        fun mapToJson(m: LinkedHashMap<*, *>): String {
            val sb = StringBuilder()
            m.forEach { k, v ->
                if (sb.isNotEmpty()) sb.append(", ")
                sb.append("\"$k\": \"$v\"")
            }
            return "{ $sb }"
        }
    }
}
