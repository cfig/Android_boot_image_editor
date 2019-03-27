package avb

import avb.alg.Algorithms
import cfig.Helper
import cfig.io.Struct
import org.slf4j.LoggerFactory
import java.security.MessageDigest

class Blob {
    companion object {
        private val log = LoggerFactory.getLogger(Blob::class.java)

        //encoded_descriptors + encoded_key + pkmd_blob + (padding)
        fun getAuxDataBlob(encodedDesc: ByteArray, encodedKey: ByteArray, pkmdBlob: ByteArray): ByteArray {
            val auxSize = Helper.round_to_multiple(
                    (encodedDesc.size + encodedKey.size + pkmdBlob.size).toLong(),
                    64)
            return Struct("${auxSize}b").pack(Helper.join(encodedDesc, encodedKey, pkmdBlob))
        }

        fun getAuthBlob(header_data_blob: ByteArray,
                        aux_data_blob: ByteArray,
                        algorithm_name: String): ByteArray {
            val alg = Algorithms.get(algorithm_name)!!
            val authBlockSize = Helper.round_to_multiple((alg.hash_num_bytes + alg.signature_num_bytes).toLong(), 64)
            if (0L == authBlockSize) {
                log.info("No auth blob")
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
                binarySignature = Helper.rawSign(alg.defaultKey.replace(".pem", ".pk8"), Helper.join(alg.padding, binaryHash))
            }
            val authData = Helper.join(binaryHash, binarySignature)
            return Helper.join(authData, Struct("${authBlockSize - authData.size}x").pack(0))
        }
    }
}
