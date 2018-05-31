package cfig

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.slf4j.LoggerFactory
import java.io.File

class Signer {
    companion object {
        private val log = LoggerFactory.getLogger(Signer::class.java)
        private val workDir = UnifiedConfig.workDir

        fun sign(avbtool: String, bootSigner: String) {
            log.info("Loading config from ${workDir}bootimg.json")
            val cfg = ObjectMapper().readValue(File(workDir + "bootimg.json"), UnifiedConfig::class.java)
            val readBack = cfg.toArgs()
            val args = readBack[0] as ImgArgs
            val info = readBack[1] as ImgInfo

            when (args.verifyType) {
                ImgArgs.VerifyType.VERIFY -> {
                    log.info("Signing with verified-boot 1.0 style")
                    val sig = ObjectMapper().readValue(
                            mapToJson(info.signature as LinkedHashMap<*, *>), ImgInfo.VeritySignature::class.java)
                    DefaultExecutor().execute(CommandLine.parse("java -jar $bootSigner " +
                            "${sig.path} ${args.output}.clear ${sig.verity_pk8} ${sig.verity_pem} ${args.output}.signed"))
                }
                ImgArgs.VerifyType.AVB -> {
                    log.info("Adding hash_footer with verified-boot 2.0 style")
                    val sig = ObjectMapper().readValue(
                            mapToJson(info.signature as LinkedHashMap<*, *>), ImgInfo.AvbSignature::class.java)
                    File(args.output + ".clear").copyTo(File(args.output + ".signed"))
                    val cmdlineStr = "$avbtool add_hash_footer " +
                            "--image ${args.output}.signed " +
                            "--partition_size ${sig.imageSize} " +
                            "--salt ${sig.salt} " +
                            "--partition_name ${sig.partName} " +
                            "--hash_algorithm ${sig.hashAlgorithm} " +
                            "--algorithm ${sig.algorithm} " +
                            "--key avb/avb_test_data/testkey_rsa4096.pem"
                    log.warn(cmdlineStr)
                    DefaultExecutor().execute(CommandLine.parse(cmdlineStr))
                    verifyAVBIntegrity(args, avbtool)

                    File(args.output + ".clear").copyTo(File(args.output + ".signed2"))
                    Avb().add_hash_footer(args.output + ".signed2",
                            sig.imageSize!!.toLong(),
                            false, false,
                            salt = sig.salt,
                            hash_algorithm = sig.hashAlgorithm!!,
                            partition_name = sig.partName!!,
                            rollback_index = 0,
                            common_algorithm = sig.algorithm!!,
                            common_key_path = "avb/avb_test_data/testkey_rsa4096.pem")
                }
            }
        }

        private fun verifyAVBIntegrity(args: ImgArgs, avbtool: String) {
            val tgt = args.output + ".signed"
            log.info("Verifying AVB: $tgt")
            DefaultExecutor().execute(CommandLine.parse("$avbtool verify_image --image $tgt"))
            log.info("Verifying image passed: $tgt")
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