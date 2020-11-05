package cfig.helper

import cfig.io.Struct3
import com.google.common.math.BigIntegerMath
import org.apache.commons.codec.binary.Hex
import org.bouncycastle.util.io.pem.PemReader
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.math.BigInteger
import java.math.RoundingMode
import java.security.Security

class KeyHelper {
    companion object {
        private val log = LoggerFactory.getLogger(KeyHelper::class.java)

        @Throws(IllegalArgumentException::class)
        fun parsePemPubkey(inputStream: InputStream): org.bouncycastle.asn1.pkcs.RSAPublicKey {
            val p = PemReader(InputStreamReader(inputStream)).readPemObject()
            if ("RSA PUBLIC KEY" != p.type) {
                throw IllegalArgumentException("input is not valid 'RSA PUBLIC KEY'")
            }
            return org.bouncycastle.asn1.pkcs.RSAPublicKey.getInstance(p.content)
        }

        @Throws(IllegalArgumentException::class)
        fun parsePemPrivateKeyBC(inputStream: InputStream): org.bouncycastle.asn1.pkcs.RSAPrivateKey {
            val p = PemReader(InputStreamReader(inputStream)).readPemObject()
            if ("RSA PRIVATE KEY" != p.type) {
                throw IllegalArgumentException("input is not valid 'RSA PRIVATE KEY'")
            }
            return org.bouncycastle.asn1.pkcs.RSAPrivateKey.getInstance(p.content)
        }

//            fun parsePemPrivateKey(inputStream: InputStream): java.security.PrivateKey {
//                val rsa = BC.parsePemPrivateKeyBC(inputStream)
//                return generateRsaPrivateKey(rsa.modulus, rsa.privateExponent)
//            }


        /*
            read RSA private key
            assert exp == 65537
            num_bits = log2(modulus)

            @return: AvbRSAPublicKeyHeader formatted bytearray
                    https://android.googlesource.com/platform/external/avb/+/master/libavb/avb_crypto.h#158
            from avbtool::encode_rsa_key()
         */
        fun encodeRSAkey(key: ByteArray): ByteArray {
            val rsa = parsePemPrivateKeyBC(ByteArrayInputStream(key))
            assert(65537.toBigInteger() == rsa.publicExponent)
            val numBits: Int = BigIntegerMath.log2(rsa.modulus, RoundingMode.CEILING)
            log.debug("modulus: " + rsa.modulus)
            log.debug("numBits: $numBits")
            val b = BigInteger.valueOf(2).pow(32)
            val n0inv = (b - rsa.modulus.modInverse(b)).toLong()
            log.debug("n0inv = $n0inv")
            val r = BigInteger.valueOf(2).pow(numBits)
            val rrModn = (r * r).mod(rsa.modulus)
            log.debug("BB: " + numBits / 8 + ", mod_len: " + rsa.modulus.toByteArray().size + ", rrmodn = " + rrModn.toByteArray().size)
            val unsignedModulo = rsa.modulus.toByteArray().sliceArray(1..numBits / 8) //remove sign byte
            log.debug("unsigned modulo: " + Hex.encodeHexString(unsignedModulo))
            val ret = Struct3("!II${numBits / 8}b${numBits / 8}b").pack(
                    numBits,
                    n0inv,
                    unsignedModulo,
                    rrModn.toByteArray())
            log.debug("rrmodn: " + Hex.encodeHexString(rrModn.toByteArray()))
            log.debug("RSA: " + Hex.encodeHexString(ret))
            return ret
        }

        fun listAll() {
            Security.getProviders().forEach {
                val sb = StringBuilder("Provider: " + it.name + "{")
                it.stringPropertyNames().forEach { key ->
                    sb.append(" (k=" + key + ",v=" + it.getProperty(key) + "), ")
                }
                sb.append("}")
                log.info(sb.toString())
            }

            var i = 0
            for (item in Security.getAlgorithms("Cipher")) {
                log.info("Cipher: $i -> $item")
                i++
            }
        }
    }
}
