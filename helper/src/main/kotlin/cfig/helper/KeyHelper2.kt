package cfig.helper

import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.apache.commons.exec.ExecuteException
import org.apache.commons.exec.PumpStreamHandler
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.util.*
import javax.crypto.Cipher

class KeyHelper2 {
    companion object {
        private val log = LoggerFactory.getLogger(KeyHelper2::class.java)

        //inspired by
        //  https://stackoverflow.com/questions/40242391/how-can-i-sign-a-raw-message-without-first-hashing-it-in-bouncy-castle
        // "specifying Cipher.ENCRYPT mode or Cipher.DECRYPT mode doesn't make a difference;
        //      both simply perform modular exponentiation"
        fun rawSign(privk: java.security.PrivateKey, data: ByteArray): ByteArray {
            return Cipher.getInstance("RSA/ECB/NoPadding").let { cipher ->
                cipher.init(Cipher.ENCRYPT_MODE, privk)
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

        fun sign(inData: ByteArray, privateKey: PrivateKey): String {
            val signature = Signature.getInstance("SHA256withRSA").let {
                it.initSign(privateKey)
                it.update(inData)
                it.sign()
            }
            return Base64.getEncoder().encodeToString(signature)
        }

        fun verify(inData: ByteArray, signature: ByteArray, pubKey: PublicKey): Boolean {
            return Signature.getInstance("SHA256withRSA").let {
                it.initVerify(pubKey)
                it.update(inData)
                it.verify(signature)
            }
        }


        fun verify(inData: ByteArray, base64Signature: String, pubKey: PublicKey): Boolean {
            val signatureBytes = Base64.getDecoder().decode(base64Signature)
            return Signature.getInstance("SHA256withRSA").let {
                it.initVerify(pubKey)
                it.update(inData)
                it.verify(signatureBytes)
            }
        }

        fun verify2(inData: ByteArray, encrypedHash: ByteArray, inKey: java.security.PublicKey): Boolean {
            val calcHash = sha256(inData)
            val decrypedHash = Cipher.getInstance("RSA").let {
                it.init(Cipher.DECRYPT_MODE, inKey)
                it.doFinal(encrypedHash)
            }
            return calcHash.contentEquals(decrypedHash)
        }
    }
}
