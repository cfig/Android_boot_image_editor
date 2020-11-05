package avb.blob

import avb.alg.Algorithms
import cfig.helper.Helper
import cfig.helper.KeyHelper2
import cfig.io.Struct3
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest

@OptIn(ExperimentalUnsignedTypes::class)
data class AuthBlob(
        var offset: Long = 0,
        var size: Long = 0,
        var hash: String? = null,
        var signature: String? = null) {
    companion object {
        fun createBlob(header_data_blob: ByteArray,
                        aux_data_blob: ByteArray,
                        algorithm_name: String): ByteArray {
            val alg = Algorithms.get(algorithm_name)!!
            val authBlockSize = Helper.round_to_multiple((alg.hash_num_bytes + alg.signature_num_bytes).toLong(), 64)
            if (0L == authBlockSize) {
                log.info("No auth blob for algorithm " + alg.name)
                return byteArrayOf()
            }

            //hash & signature
            var binaryHash: ByteArray = byteArrayOf()
            var binarySignature: ByteArray = byteArrayOf()
            if (algorithm_name != "NONE") {
                val hasher = MessageDigest.getInstance(Helper.pyAlg2java(alg.hash_name))
                binaryHash = hasher.apply {
                    update(header_data_blob)
                    update(aux_data_blob)
                }.digest()
                val k = KeyHelper2.parseRsaPk8(Files.readAllBytes(Paths.get(alg.defaultKey.replace(".pem", ".pk8"))))
                binarySignature = KeyHelper2.rawSign(k, Helper.join(alg.padding, binaryHash))
            }
            val authData = Helper.join(binaryHash, binarySignature)
            return Helper.join(authData, Struct3("${authBlockSize - authData.size}x").pack(0))
        }

        private val log = LoggerFactory.getLogger(AuthBlob::class.java)
    }
}
