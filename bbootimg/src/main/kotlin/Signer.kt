package cfig

import avb.AVBInfo
import avb.alg.Algorithms
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
            val readBack = UnifiedConfig.readBack()
            val args = readBack[0] as ImgArgs

            when (args.verifyType) {
                ImgArgs.VerifyType.VERIFY -> {
                    log.info("Signing with verified-boot 1.0 style")
                    val sig = readBack[2] as ImgInfo.VeritySignature
                    DefaultExecutor().execute(CommandLine.parse("java -jar $bootSigner " +
                            "${sig.path} ${args.output}.clear ${sig.verity_pk8} ${sig.verity_pem} ${args.output}.signed"))
                }
                ImgArgs.VerifyType.AVB -> {
                    log.info("Adding hash_footer with verified-boot 2.0 style")
                    val sig = readBack[2] as ImgInfo.AvbSignature
                    val ai = ObjectMapper().readValue(File(Avb.getJsonFileName(args.output)), AVBInfo::class.java)
                    //val alg = Algorithms.get(ai.header!!.algorithm_type.toInt())

                    //our signer
                    File(args.output + ".clear").copyTo(File(args.output + ".signed"))
                    Avb().add_hash_footer(args.output + ".signed",
                            sig.imageSize!!.toLong(),
                            false,
                            false,
                            salt = sig.salt,
                            hash_algorithm = sig.hashAlgorithm!!,
                            partition_name = sig.partName!!,
                            rollback_index = ai.header!!.rollback_index,
                            common_algorithm = sig.algorithm!!,
                            inReleaseString = ai.header!!.release_string)
                    //original signer
                    File(args.output + ".clear").copyTo(File(args.output + ".signed2"))
                    val signKey = Algorithms.get(sig.algorithm!!)
                    var cmdlineStr = "$avbtool add_hash_footer " +
                            "--image ${args.output}.signed2 " +
                            "--partition_size ${sig.imageSize} " +
                            "--salt ${sig.salt} " +
                            "--partition_name ${sig.partName} " +
                            "--hash_algorithm ${sig.hashAlgorithm} " +
                            "--algorithm ${sig.algorithm} "
                    if (signKey!!.defaultKey.isNotBlank()) {
                        cmdlineStr += "--key ${signKey.defaultKey}"
                    }
                    log.warn(cmdlineStr)
                    val cmdLine = CommandLine.parse(cmdlineStr)
                    cmdLine.addArgument("--internal_release_string")
                    cmdLine.addArgument(ai.header!!.release_string, false)
                    DefaultExecutor().execute(cmdLine)
                    verifyAVBIntegrity(args, avbtool)
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
