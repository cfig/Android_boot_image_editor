package cfig

import org.bouncycastle.asn1.pkcs.RSAPrivateKey
import org.bouncycastle.util.io.pem.PemReader
import java.io.InputStream
import java.io.InputStreamReader
import java.math.BigInteger
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPrivateKeySpec
import java.security.spec.RSAPublicKeySpec

class KeyUtil {
    companion object {
        @Throws(IllegalArgumentException::class)
        fun parsePemPrivateKey(inputStream: InputStream): RSAPrivateKey {
            val p = PemReader(InputStreamReader(inputStream)).readPemObject()
            if ("RSA PRIVATE KEY" != p.type) {
                throw IllegalArgumentException("input is not valid 'RSA PRIVATE KEY'")
            }
            return RSAPrivateKey.getInstance(p.content)
        }

        fun parsePemPrivateKey2(inputStream: InputStream): PrivateKey {
            val rsa = parsePemPrivateKey(inputStream)
            return generateRsaPrivateKey(rsa.modulus, rsa.privateExponent)
        }

        @Throws(Exception::class)
        fun parsePk8PrivateKey(inputData: ByteArray): PrivateKey {
            val spec = PKCS8EncodedKeySpec(inputData)
            return KeyFactory.getInstance("RSA").generatePrivate(spec)
        }

        @Throws(Exception::class)
        private fun generateRsaPublicKey(modulus: BigInteger, publicExponent: BigInteger): PublicKey {
            return KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(modulus, publicExponent))
        }

        @Throws(Exception::class)
        private fun generateRsaPrivateKey(modulus: BigInteger, privateExponent: BigInteger): PrivateKey {
            return KeyFactory.getInstance("RSA").generatePrivate(RSAPrivateKeySpec(modulus, privateExponent))
        }
    }
}