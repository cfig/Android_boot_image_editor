package cfig.helper

import org.apache.commons.codec.binary.Hex
import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.apache.commons.exec.ExecuteException
import org.apache.commons.exec.PumpStreamHandler
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPrivateKeySpec
import java.security.spec.RSAPublicKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*
import javax.crypto.Cipher

class KeyHelper2 {
    companion object {
        private val log = LoggerFactory.getLogger(KeyHelper2::class.java)

        fun parseRsaPk8(inputData: ByteArray): java.security.PrivateKey {
            val spec = PKCS8EncodedKeySpec(inputData)
            return KeyFactory.getInstance("RSA").generatePrivate(spec)
        }

        fun parseRsaKey(keyText: String): PrivateKey {
            val publicKeyPEM = keyText
                    .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                    .replace(System.lineSeparator().toRegex(), "")
                    .replace("\n", "")
                    .replace("\r", "")
                    .replace("-----END RSA PRIVATE KEY-----", "")
            log.warn("trimmed key")
            log.warn(publicKeyPEM)
            val encoded: ByteArray = Base64.getDecoder().decode(publicKeyPEM)
            val keySpec = X509EncodedKeySpec(encoded)
            return KeyFactory.getInstance("RSA").generatePrivate(keySpec)
        }

        fun parsePemPubkey(keyText: String): java.security.interfaces.RSAPublicKey {
            val publicKeyPEM = keyText
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace(System.lineSeparator().toRegex(), "")
                    .replace("\n", "")
                    .replace("\r", "")
                    .replace("-----END PUBLIC KEY-----", "")
            val encoded: ByteArray = Base64.getDecoder().decode(publicKeyPEM)
            val keySpec = X509EncodedKeySpec(encoded)
            return KeyFactory.getInstance("RSA").generatePublic(keySpec) as java.security.interfaces.RSAPublicKey
        }


        fun parsePemPubCert(keyText: String) {
            val publicKeyPEM = keyText
                    .replace("-----BEGIN CERTIFICATE-----", "")
                    .replace(System.lineSeparator().toRegex(), "")
                    .replace("\n", "")
                    .replace("\r", "")
                    .replace("-----END CERTIFICATE-----", "")
            val encoded: ByteArray = Base64.getDecoder().decode(publicKeyPEM)
            val keySpec = X509EncodedKeySpec(encoded)
//            return KeyFactory.getInstance("RSA").generatePublic(keySpec) as java.security.interfaces.RSAPublicKey
        }

        /*
          in: modulus, expo
          out: PublicKey
        */
        fun generateRsaPublicKey(modulus: BigInteger, publicExponent: BigInteger): java.security.PublicKey {
            return KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(modulus, publicExponent))
        }

        /*
            in: modulus, expo
            out: PrivateKey
        */
        fun generateRsaPrivateKey(modulus: BigInteger, privateExponent: BigInteger): java.security.PrivateKey {
            return KeyFactory.getInstance("RSA").generatePrivate(RSAPrivateKeySpec(modulus, privateExponent))
        }

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
            log.debug("raw input: " + Hex.encodeHexString(data))
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
                log.debug("OUT: " + Hex.encodeHexString(stdout.toByteArray()))
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
