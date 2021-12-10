// Copyright 2021 yuyezhong@gmail.com
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package avb.blob

import avb.alg.Algorithms
import cfig.helper.CryptoHelper
import cfig.helper.Helper
import cfig.io.Struct3
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest
import java.security.PrivateKey

data class AuthBlob(
    var offset: Long = 0,
    var size: Long = 0,
    var hash: String? = null,
    var signature: String? = null
) {
    companion object {
        fun calcHash(
            header_data_blob: ByteArray,
            aux_data_blob: ByteArray,
            algorithm_name: String
        ): ByteArray {
            val alg = Algorithms.get(algorithm_name)!!
            return if (alg.name == "NONE") {
                log.debug("calc hash: NONE")
                byteArrayOf()
            } else {
                MessageDigest.getInstance(Helper.pyAlg2java(alg.hash_name)).apply {
                    update(header_data_blob)
                    update(aux_data_blob)
                }.digest().apply {
                    log.debug("calc hash = " + Helper.toHexString(this))
                }
            }
        }

        private fun calcSignature(hash: ByteArray, algorithm_name: String): ByteArray {
            val alg = Algorithms.get(algorithm_name)!!
            return if (alg.name == "NONE") {
                byteArrayOf()
            } else {
                val k = CryptoHelper.KeyBox.parse2(Files.readAllBytes(Paths.get(alg.defaultKey.replace(".pem", ".pk8")))) as Array<*>
                assert(k[0] as Boolean)
                CryptoHelper.Signer.rawRsa(k[2] as PrivateKey, Helper.join(alg.padding, hash))
            }
        }

        fun createBlob(
            header_data_blob: ByteArray,
            aux_data_blob: ByteArray,
            algorithm_name: String
        ): ByteArray {
            val alg = Algorithms.get(algorithm_name)!!
            val authBlockSize = Helper.round_to_multiple((alg.hash_num_bytes + alg.signature_num_bytes).toLong(), 64)
            if (0L == authBlockSize) {
                log.info("No auth blob for algorithm " + alg.name)
                return byteArrayOf()
            }

            //hash & signature
            val binaryHash = calcHash(header_data_blob, aux_data_blob, algorithm_name)
            val binarySignature = calcSignature(binaryHash, algorithm_name)
            val authData = Helper.join(binaryHash, binarySignature)
            return Helper.join(authData, Struct3("${authBlockSize - authData.size}x").pack(0))
        }

        private val log = LoggerFactory.getLogger(AuthBlob::class.java)
    }
}
