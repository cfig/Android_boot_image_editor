package cfig.bootimg

import avb.AVBInfo
import avb.alg.Algorithms
import cfig.Avb
import cfig.Avb.Companion.getJsonFileName
import cfig.helper.Helper
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.slf4j.LoggerFactory
import java.io.File
import cfig.EnvironmentVerifier

class Signer {
    @OptIn(ExperimentalUnsignedTypes::class)
    companion object {
        private val log = LoggerFactory.getLogger(Signer::class.java)

        fun signAVB(output: String, imageSize: Long, avbtool: String) {
            log.info("Adding hash_footer with verified-boot 2.0 style")
            val ai = ObjectMapper().readValue(File(getJsonFileName(output)), AVBInfo::class.java)
            val alg = Algorithms.get(ai.header!!.algorithm_type.toInt())
            val bootDesc = ai.auxBlob!!.hashDescriptors[0]
            val newAvbInfo = ObjectMapper().readValue(File(getJsonFileName(output)), AVBInfo::class.java)

            //our signer
            File("$output.clear").copyTo(File("$output.signed"), overwrite = true)
            Avb().addHashFooter("$output.signed",
                    imageSize,
                    partition_name = bootDesc.partition_name,
                    newAvbInfo = newAvbInfo)
            //original signer
            val cmdPrefix = if (EnvironmentVerifier().isWindows) "python " else ""
            CommandLine.parse(cmdPrefix + "$avbtool add_hash_footer").apply {
                addArguments("--image ${output}.signed2")
                addArguments("--partition_size ${imageSize}")
                addArguments("--salt ${Helper.toHexString(bootDesc.salt)}")
                addArguments("--partition_name ${bootDesc.partition_name}")
                addArguments("--hash_algorithm ${bootDesc.hash_algorithm}")
                addArguments("--algorithm ${alg!!.name}")
                addArguments("--rollback_index ${ai.header!!.rollback_index}")
                if (alg.defaultKey.isNotBlank()) {
                    addArguments("--key ${alg.defaultKey}")
                }
                newAvbInfo.auxBlob?.let { newAuxblob ->
                    newAuxblob.propertyDescriptor.forEach { newProp ->
                        addArguments(arrayOf("--prop", "${newProp.key}:${newProp.value}"))
                    }
                }
                addArgument("--internal_release_string")
                addArgument(ai.header!!.release_string, false)
                log.info(this.toString())

                File("$output.clear").copyTo(File("$output.signed2"), overwrite = true)
                DefaultExecutor().execute(this)
            }
            Common.assertFileEquals("$output.signed", "$output.signed2")
            //TODO: decide what to verify
            //Parser.verifyAVBIntegrity(cfg.info.output + ".signed", avbtool)
            //Parser.verifyAVBIntegrity(cfg.info.output + ".signed2", avbtool)
        }

        fun signVB1(src: String, tgt: String) {
            val bootSigner = Helper.prop("bootSigner")
            log.info("Signing with verified-boot 1.0 style")
            val sig = Common.VeritySignature(
                    verity_pk8 = Helper.prop("verity_pk8"),
                    verity_pem = Helper.prop("verity_pem"),
                    jarPath = Helper.prop("bootSigner")
            )
            val bootSignCmd = "java -jar $bootSigner " +
                    "${sig.path} $src " +
                    "${sig.verity_pk8} ${sig.verity_pem} " +
                    "$tgt"
            log.info(bootSignCmd)
            DefaultExecutor().execute(CommandLine.parse(bootSignCmd))
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
