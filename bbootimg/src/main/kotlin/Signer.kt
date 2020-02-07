package cfig

import avb.AVBInfo
import avb.alg.Algorithms
import cfig.Avb.Companion.getJsonFileName
import cfig.bootimg.BootImgInfo
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.slf4j.LoggerFactory
import java.io.File

class Signer {
    @ExperimentalUnsignedTypes
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
                    Avb().addHashFooter(cfg.info.output + ".signed",
                            info2.imageSize,
                            partition_name = bootDesc.partition_name,
                            newAvbInfo = ObjectMapper().readValue(File(getJsonFileName(cfg.info.output)), AVBInfo::class.java))
                    //original signer
                    CommandLine.parse("$avbtool add_hash_footer").apply {
                        addArguments("--image ${cfg.info.output}.signed2")
                        addArguments("--partition_size ${info2.imageSize}")
                        addArguments("--salt ${Helper.toHexString(bootDesc.salt)}")
                        addArguments("--partition_name ${bootDesc.partition_name}")
                        addArguments("--hash_algorithm ${bootDesc.hash_algorithm}")
                        addArguments("--algorithm ${alg!!.name}")
                        if (alg.defaultKey.isNotBlank()) {
                            addArguments("--key ${alg.defaultKey}")
                        }
                        addArgument("--internal_release_string")
                        addArgument(ai.header!!.release_string, false)
                        log.warn(this.toString())

                        File(cfg.info.output + ".clear").copyTo(File(cfg.info.output + ".signed2"))
                        DefaultExecutor().execute(this)
                    }
                    //TODO: decide what to verify
                    //Parser.verifyAVBIntegrity(cfg.info.output + ".signed", avbtool)
                    //Parser.verifyAVBIntegrity(cfg.info.output + ".signed2", avbtool)
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
