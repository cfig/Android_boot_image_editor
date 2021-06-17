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

import cfig.io.Struct3
import com.google.common.math.BigIntegerMath
import org.bouncycastle.util.io.pem.PemReader
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.math.BigInteger
import java.math.RoundingMode
import java.security.KeyFactory
import java.security.Security
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPrivateKeySpec
import java.security.spec.RSAPublicKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*

/*
https://docs.oracle.com/javase/9/security/java-pki-programmers-guide.htm#JSSEC-GUID-650D0D53-B617-4055-AFD3-AF5C2629CBBF
https://www.baeldung.com/java-read-pem-file-keys
 */
class KeyHelper {
    companion object {
        private val log = LoggerFactory.getLogger(KeyHelper::class.java)

        fun getPemContent(keyText: String): ByteArray {
            val publicKeyPEM = keyText
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replace(System.lineSeparator().toRegex(), "")
                .replace("\n", "")
                .replace("\r", "")
            return Base64.getDecoder().decode(publicKeyPEM)
        }

        /*
          in: modulus, public expo
          out: PublicKey

          in: modulus, private expo
          out: PrivateKey
        */
        fun makeKey(modulus: BigInteger, exponent: BigInteger, isPublicExpo: Boolean): Any {
            return if (isPublicExpo) {
                KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(modulus, exponent))
            } else {
                KeyFactory.getInstance("RSA").generatePrivate(RSAPrivateKeySpec(modulus, exponent))
            }
        }

        fun parse(data: ByteArray): Any {
            val p = PemReader(InputStreamReader(ByteArrayInputStream(data))).readPemObject()
            return if (p != null) {
                log.debug("parse PEM: " + p.type)
                when (p.type) {
                    "RSA PUBLIC KEY" -> {
                        org.bouncycastle.asn1.pkcs.RSAPublicKey.getInstance(p.content) as org.bouncycastle.asn1.pkcs.RSAPublicKey
                    }
                    "RSA PRIVATE KEY" -> {
                        org.bouncycastle.asn1.pkcs.RSAPrivateKey.getInstance(p.content) as org.bouncycastle.asn1.pkcs.RSAPrivateKey
                    }
                    "PUBLIC KEY" -> {
                        val keySpec = X509EncodedKeySpec(p.content)
                        KeyFactory.getInstance("RSA").generatePublic(keySpec) as java.security.interfaces.RSAPublicKey
                    }
                    "PRIVATE KEY" -> {
                        val keySpec = PKCS8EncodedKeySpec(p.content)
                        KeyFactory.getInstance("RSA").generatePrivate(keySpec) as java.security.interfaces.RSAPrivateKey
                    }
                    "CERTIFICATE" -> {
                        CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(p.content))
                    }
                    else -> throw IllegalArgumentException("unsupported type: ${p.type}")
                }
            } else {
                try {
                    val spec = PKCS8EncodedKeySpec(data)
                    val privateKey = KeyFactory.getInstance("RSA").generatePrivate(spec)
                    log.debug("Parse PKCS8: Private")
                    privateKey
                } catch (e: java.security.spec.InvalidKeySpecException) {
                    log.debug("Parse X509: Public")
                    val spec = X509EncodedKeySpec(data)
                    KeyFactory.getInstance("RSA").generatePublic(spec)
                }
            }
        }

        /*
            read RSA private key
            assert exp == 65537
            num_bits = log2(modulus)

            @return: AvbRSAPublicKeyHeader formatted bytearray
                    https://android.googlesource.com/platform/external/avb/+/master/libavb/avb_crypto.h#158
            from avbtool::encode_rsa_key()
         */
        fun encodeRSAkey(rsa: org.bouncycastle.asn1.pkcs.RSAPrivateKey): ByteArray {
            assert(65537.toBigInteger() == rsa.publicExponent)
            val numBits: Int = BigIntegerMath.log2(rsa.modulus, RoundingMode.CEILING)
            assert(rsa.modulus.bitLength() == numBits)
            val b = BigInteger.valueOf(2).pow(32)
            val n0inv = b.minus(rsa.modulus.modInverse(b)).toLong()
            val rrModn = BigInteger.valueOf(4).pow(numBits).rem(rsa.modulus)
            val unsignedModulo = rsa.modulus.toByteArray().sliceArray(1..numBits / 8) //remove sign byte
            return Struct3("!II${numBits / 8}b${numBits / 8}b").pack(
                numBits,
                n0inv,
                unsignedModulo,
                rrModn.toByteArray()
            )
        }

        fun decodeRSAkey(key: ByteArray): java.security.interfaces.RSAPublicKey {
            val ret = Struct3("!II").unpack(ByteArrayInputStream(key))
            val numBits = (ret[0] as UInt).toInt()
            val n0inv = (ret[1] as UInt).toLong()
            val ret2 = Struct3("!II${numBits / 8}b${numBits / 8}b").unpack(ByteArrayInputStream(key))
            val unsignedModulo = ret2[2] as ByteArray
            val rrModn = BigInteger(ret2[3] as ByteArray)
            log.debug("n0inv=$n0inv, unsignedModulo=${Helper.toHexString(unsignedModulo)}, rrModn=$rrModn")
            val exponent = 65537L
            val modulus = BigInteger(Helper.join(Struct3("x").pack(0), unsignedModulo))
            val keySpec = RSAPublicKeySpec(modulus, BigInteger.valueOf(exponent))
            return KeyFactory.getInstance("RSA").generatePublic(keySpec) as java.security.interfaces.RSAPublicKey
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

            for ((i, item) in Security.getAlgorithms("Cipher").withIndex()) {
                log.info("Cipher: $i -> $item")
            }
        }
    }
}
