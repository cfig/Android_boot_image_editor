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

package cfig.helper

import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.apache.commons.exec.ExecuteException
import org.apache.commons.exec.PumpStreamHandler
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import javax.crypto.Cipher

class KeyHelper2 {
    companion object {
        private val log = LoggerFactory.getLogger(KeyHelper2::class.java)

        /* inspired by
         https://stackoverflow.com/questions/40242391/how-can-i-sign-a-raw-message-without-first-hashing-it-in-bouncy-castle
         "specifying Cipher.ENCRYPT mode or Cipher.DECRYPT mode doesn't make a difference;
              both simply perform modular exponentiation"

        python counterpart:
          import Crypto.PublicKey.RSA
          key = Crypto.PublicKey.RSA.construct((modulus, exponent))
          vRet = key.verify(decode_long(padding_and_digest), (decode_long(sig_blob), None))
          print("verify padded digest: %s" % binascii.hexlify(padding_and_digest))
          print("verify sig: %s" % binascii.hexlify(sig_blob))
          print("X: Verify: %s" % vRet)
         */
        fun rawRsa(key: java.security.Key, data: ByteArray): ByteArray {
            return Cipher.getInstance("RSA/ECB/NoPadding").let { cipher ->
                cipher.init(Cipher.ENCRYPT_MODE, key)
                cipher.update(data)
                cipher.doFinal()
            }
        }

        fun rawSignOpenSsl(keyPath: String, data: ByteArray): ByteArray {
            log.debug("raw input: " + Helper.toHexString(data))
            log.debug("Raw sign data size = ${data.size}, key = $keyPath")
            var ret = byteArrayOf()
            val exe = DefaultExecutor()
            val stdin = ByteArrayInputStream(data)
            val stdout = ByteArrayOutputStream()
            val stderr = ByteArrayOutputStream()
            exe.streamHandler = PumpStreamHandler(stdout, stderr, stdin)
            try {
                exe.execute(CommandLine.parse("openssl rsautl -sign -inkey $keyPath -raw"))
                ret = stdout.toByteArray()
                log.debug("Raw signature size = " + ret.size)
            } catch (e: ExecuteException) {
                log.error("Execute error")
            } finally {
                log.debug("OUT: " + Helper.toHexString(stdout.toByteArray()))
                log.debug("ERR: " + String(stderr.toByteArray()))
            }

            if (ret.isEmpty()) throw RuntimeException("raw sign failed")

            return ret
        }

        fun pyAlg2java(alg: String): String {
            return when (alg) {
                "sha1" -> "sha-1"
                "sha224" -> "sha-224"
                "sha256" -> "sha-256"
                "sha384" -> "sha-384"
                "sha512" -> "sha-512"
                else -> throw IllegalArgumentException("unknown algorithm: [$alg]")
            }
        }

        /*
            openssl dgst -sha256 <file>
        */
        fun sha256(inData: ByteArray): ByteArray {
            return MessageDigest.getInstance("SHA-256").digest(inData)
        }

        fun rsa(inData: ByteArray, inKey: java.security.PrivateKey): ByteArray {
            return Cipher.getInstance("RSA").let {
                it.init(Cipher.ENCRYPT_MODE, inKey)
                it.doFinal(inData)
            }
        }

        fun sha256rsa(inData: ByteArray, inKey: java.security.PrivateKey): ByteArray {
            return rsa(sha256(inData), inKey)
        }
    }
}
