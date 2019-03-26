package avb

import avb.alg.Algorithm
import avb.alg.Algorithms
import cfig.Helper
import cfig.io.Struct
import org.junit.Assert
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest

class Blob {
    companion object {
        fun encodePubKey(alg: Algorithm, key: ByteArray? = null): ByteArray {
            var encodedKey = byteArrayOf()
            var algKey: ByteArray? = key
            if (alg.public_key_num_bytes > 0) {
                if (key == null) {
                    algKey = Files.readAllBytes((Paths.get(alg.defaultKey)))
                }
                encodedKey = Helper.encodeRSAkey(algKey!!)
                log.info("encodePubKey(): size = ${alg.public_key_num_bytes}, algorithm key size: ${encodedKey.size}")
                Assert.assertEquals(alg.public_key_num_bytes, encodedKey.size)
            } else {
                log.info("encodePubKey(): No key to encode for algorithm " + alg.name)
            }
            return encodedKey
        }

        //TODO: support pkmd_blob
        //encoded_descriptors + encoded_key + pkmd_blob + (padding)
        fun getAuxDataBlob(encodedDesc: ByteArray, encodedKey: ByteArray): ByteArray {
            val auxSize = Helper.round_to_multiple(
                    encodedDesc.size + encodedKey.size /* encoded key */ + 0L /* pkmd_blob */,
                    64)
            return Struct("${auxSize}b").pack(Helper.join(encodedDesc, encodedKey))
        }

        fun getAuthBlob(header_data_blob: ByteArray,
                        aux_data_blob: ByteArray,
                        algorithm_name: String): ByteArray {
            val alg = Algorithms.get(algorithm_name)!!
            val authBlockSize = Helper.round_to_multiple((alg.hash_num_bytes + alg.signature_num_bytes).toLong(), 64)
            if (authBlockSize == 0L) {
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

        private val log = LoggerFactory.getLogger(Blob::class.java)
    }
}