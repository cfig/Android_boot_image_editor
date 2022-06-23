@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package cfig.helper

import cc.cfig.io.Struct
import com.google.common.math.BigIntegerMath
import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.apache.commons.exec.ExecuteException
import org.apache.commons.exec.PumpStreamHandler
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.bouncycastle.util.io.pem.PemReader
import org.slf4j.LoggerFactory
import java.io.*
import java.math.BigInteger
import java.math.RoundingMode
import java.security.*
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPrivateKeySpec
import java.security.spec.RSAPublicKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*
import javax.crypto.Cipher
import kotlin.reflect.KClass

class CryptoHelper {
    enum class KeyFormat {
        PEM,  //header + metadata + base64 der
        DER, // der format
    }

    class KeyBox(val fmt: KeyFormat, val clazz: KClass<*>, val key: Any) {
        companion object {
            fun parse4(data: ByteArray): KeyBox {
                var ret: Any = false
                var retClazz: KClass<*>

                val p = PemReader(InputStreamReader(ByteArrayInputStream(data))).readPemObject()
                if (p != null) {
                    log.debug("parse PEM: " + p.type)
                    ret = when (p.type) {
                        "RSA PUBLIC KEY", "PUBLIC KEY" -> {
                            try {
                                retClazz = org.bouncycastle.asn1.pkcs.RSAPublicKey::class
                                org.bouncycastle.asn1.pkcs.RSAPublicKey.getInstance(p.content) as org.bouncycastle.asn1.pkcs.RSAPublicKey
                            } catch (e: Exception) {
                                val keySpec = X509EncodedKeySpec(p.content)
                                retClazz = java.security.interfaces.RSAPublicKey::class
                                KeyFactory.getInstance("RSA")
                                    .generatePublic(keySpec) as java.security.interfaces.RSAPublicKey
                            }
                        }
                        "RSA PRIVATE KEY", "PRIVATE KEY" -> {
                            try {
                                retClazz = org.bouncycastle.asn1.pkcs.RSAPrivateKey::class
                                org.bouncycastle.asn1.pkcs.RSAPrivateKey.getInstance(p.content) as org.bouncycastle.asn1.pkcs.RSAPrivateKey
                            } catch (e: Exception) {
                                retClazz = sun.security.rsa.RSAPrivateCrtKeyImpl::class
                                val keySpec = PKCS8EncodedKeySpec(p.content)
                                KeyFactory.getInstance("RSA")
                                    .generatePrivate(keySpec) as sun.security.rsa.RSAPrivateCrtKeyImpl
                            }
                        }
                        "CERTIFICATE REQUEST" -> {
                            retClazz = PKCS10CertificationRequest::class
                            PKCS10CertificationRequest(p.content)
                        }
                        "CERTIFICATE" -> {
                            retClazz = Certificate::class
                            CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(p.content))
                        }
                        else -> throw IllegalArgumentException("unsupported type: ${p.type}")
                    }
                    return KeyBox(KeyFormat.PEM, retClazz, ret)
                } else {
                    var bSuccess = false
                    //try 1
                    try {
                        val spec = PKCS8EncodedKeySpec(data)
                        val privateKey = KeyFactory.getInstance("RSA").generatePrivate(spec)
                        log.debug("Parse PKCS8:Private")
                        ret = privateKey
                        bSuccess = true
                    } catch (e: java.security.spec.InvalidKeySpecException) {
                        log.info("not PKCS8:Private")
                    }
                    if (bSuccess) return KeyBox(
                        KeyFormat.DER,
                        PrivateKey::class,
                        ret
                    )
                    //try 2
                    try {
                        log.debug("Parse X509:Public")
                        val spec = X509EncodedKeySpec(data)
                        ret = KeyFactory.getInstance("RSA").generatePublic(spec)
                        bSuccess = true
                    } catch (e: java.security.spec.InvalidKeySpecException) {
                        log.debug(e.toString())
                        log.debug("not X509:Public")
                    }
                    if (bSuccess) return KeyBox(KeyFormat.DER, String::class, ret)

                    //try 3: jks
                    try {
                        val envPassword = System.getProperty("password") ?: "secretpassword"
                        log.warn("trying with password=$envPassword")
                        val ks = KeyStore.getInstance("JKS")
                        ks.load(ByteArrayInputStream(data), envPassword.toCharArray())
                        ret = ks
                        bSuccess = true
                    } catch (e: IOException) {
                        if (e.toString().contains("Keystore was tampered with, or password was incorrect")) {
                            log.info("JKS password wrong #1")
                            bSuccess = false
                            ret = KeyStore.getInstance("JKS")
                        }
                        if (e.toString().contains("keystore password was incorrect")) {
                            log.info("JKS password wrong #2")
                            bSuccess = false
                            ret = KeyStore.getInstance("JKS")
                        }
                    }
                    //at last
                    return KeyBox(KeyFormat.DER, String::class, ret)
                }
            }

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


            /*
                read RSA private key
                assert exp == 65537
                num_bits = log2(modulus)

                @return: AvbRSAPublicKeyHeader formatted bytearray
                        https://android.googlesource.com/platform/external/avb/+/master/libavb/avb_crypto.h#158
                from avbtool::encode_rsa_key()
             */
            fun encodeRSAkey(rsa: org.bouncycastle.asn1.pkcs.RSAPrivateKey): ByteArray {
                require(65537.toBigInteger() == rsa.publicExponent)
                val numBits: Int = BigIntegerMath.log2(rsa.modulus, RoundingMode.CEILING)
                require(rsa.modulus.bitLength() == numBits)
                val b = BigInteger.valueOf(2).pow(32)
                val n0inv = b.minus(rsa.modulus.modInverse(b)).toLong()
                val rrModn = BigInteger.valueOf(4).pow(numBits).rem(rsa.modulus).toByteArray().let {
                    it.sliceArray(it.size - numBits/8 until it.size)
                }
                val unsignedModulo = rsa.modulus.toByteArray().let {
                    it.sliceArray(it.size - numBits/8 until it.size)
                }
                return Struct("!II${numBits / 8}b${numBits / 8}b").pack(
                    numBits,
                    n0inv,
                    unsignedModulo,
                    rrModn
                )
            }

            fun decodeRSAkey(key: ByteArray): java.security.interfaces.RSAPublicKey {
                val ret = Struct("!II").unpack(ByteArrayInputStream(key))
                val numBits = (ret[0] as UInt).toInt()
                val n0inv = (ret[1] as UInt).toLong()
                val ret2 = Struct("!II${numBits / 8}b${numBits / 8}b").unpack(ByteArrayInputStream(key))
                val unsignedModulo = ret2[2] as ByteArray
                val rrModn = BigInteger(ret2[3] as ByteArray)
                log.debug("n0inv=$n0inv, unsignedModulo=${Helper.toHexString(unsignedModulo)}, rrModn=$rrModn")
                val exponent = 65537L
                val modulus = BigInteger(Helper.join(Struct("x").pack(0), unsignedModulo))
                val keySpec = RSAPublicKeySpec(modulus, BigInteger.valueOf(exponent))
                return KeyFactory.getInstance("RSA").generatePublic(keySpec) as java.security.interfaces.RSAPublicKey
            }

            fun decodePem(keyText: String): ByteArray {
                val publicKeyPEM = keyText
                    .replace("-----BEGIN .*-----".toRegex(), "")
                    .replace(System.lineSeparator().toRegex(), "")
                    .replace("\n", "")
                    .replace("\r", "")
                    .replace("-----END .*-----".toRegex(), "")
                return Base64.getDecoder().decode(publicKeyPEM)
            }
        } //end-companion
    }

    class Hasher {
        companion object {
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

            fun hash(ds: Dumpling<*>, coordinates: List<Pair<Long, Long>>, algorithm: String): ByteArray {
                require(coordinates.isNotEmpty())
                coordinates.forEach {
                    require(it.first >= 0 && it.second > 0)
                }
                return MessageDigest.getInstance(algorithm).let { md ->
                    coordinates.forEach { coordinate ->
                        ds.getInputStream().use { fis ->
                            fis.skip(coordinate.first)
                            val ibs = 1024 * 1024
                            val buffer = ByteArray(ibs)
                            var bytesRemaining = coordinate.second
                            while (bytesRemaining > 0) {
                                log.debug("Remain $bytesRemaining, reading ...")
                                val bytesRead = fis.read(buffer)
                                if (bytesRemaining > ibs) {
                                    check(bytesRead == ibs)
                                    md.update(buffer, 0, bytesRead)
                                } else {
                                    check(bytesRead >= bytesRemaining)
                                    md.update(buffer, 0, bytesRemaining.toInt())
                                }
                                bytesRemaining -= bytesRead
                                log.debug("Read $bytesRead, remain $bytesRemaining")
                            }
                        }
                    }
                    md
                }.digest()
            }

            fun hash(file: String, algorithm: String): ByteArray {
                return hash(Dumpling(file), listOf(Pair(0, File(file).length())), algorithm)
            }

            fun hash(ds: Dumpling<*>, algorithm: String): ByteArray {
                return hash(ds, listOf(Pair(0, ds.getLength())), algorithm)
            }
        }
    }

    class Signer {
        companion object {
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

            fun rsa(inData: ByteArray, inKey: java.security.PrivateKey): ByteArray {
                return Cipher.getInstance("RSA").let {
                    it.init(Cipher.ENCRYPT_MODE, inKey)
                    it.doFinal(inData)
                }
            }

            fun sha256rsa(inData: ByteArray, inKey: java.security.PrivateKey): ByteArray {
                return rsa(Hasher.sha256(inData), inKey)
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(CryptoHelper::class.java)
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
