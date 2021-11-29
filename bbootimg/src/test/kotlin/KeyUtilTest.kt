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

import avb.alg.Algorithms
import cfig.helper.CryptoHelper
import cfig.helper.Helper
import com.google.common.math.BigIntegerMath
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.math.BigInteger
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.Paths
import java.security.*
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

class KeyUtilTest {
    @Test
    fun parseKeys() {
        val keyFile = "../" + Algorithms.get("SHA256_RSA2048")!!.defaultKey
        println("Key: $keyFile")
        val k = CryptoHelper.KeyBox.parse(File(keyFile.replace("pem", "pk8")).readBytes()) as RSAPrivateKey
        println(k.privateExponent)
        println(k.modulus)
        val k2 = CryptoHelper.KeyBox.parse(File(keyFile).readBytes()) as org.bouncycastle.asn1.pkcs.RSAPrivateKey
        println(k2.privateExponent)
        println(k2.modulus)
        //KeyHelper2.parseRsaPk8(FileInputStream(keyFile).readAllBytes())
        CryptoHelper.KeyBox.parse(File(keyFile.replace("pem", "pk8")).readBytes())
    }

    @Test
    fun x1() {
        val p = BigInteger.valueOf(61L)
        val q = BigInteger.valueOf(53L)
        val modulus = p * q
        println(modulus)
        val exponent = 17
        val x = calcPrivateKey(p, q, exponent)
        println(x)
        //private key: modulus, x
        //public key: modulus, exponent

        val data = 123L
        val encryptedData = enc(data, exponent, modulus)
        println("enc2 = " + encryptedData)
        val decryptedData = dec(encryptedData, x, modulus)
        println("dec2 data = " + decryptedData)
    }

    fun calcPrivateKey(p: BigInteger, q: BigInteger, exponent: Int): Int {
        val modulus = p * q
        val phi = (p - BigInteger.ONE) * (q - BigInteger.ONE)
        return BigInteger.valueOf(exponent.toLong()).modInverse(phi).toInt()
    }

    //data^{exp}
    fun enc(data: Long, exponent: Int, modulus: BigInteger): Long {
        return BigInteger.valueOf(data).pow(exponent).rem(modulus).toLong()
    }

    //data^{privateKey}
    fun dec(data: Long, privateKey: Int, modulus: BigInteger): Long {
        return BigInteger.valueOf(data).pow(privateKey).rem(modulus).toLong()
    }

    @Test
    fun rawSignTest() {
        val data =
            Helper.fromHexString("0001ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff003031300d0609608648016503040201050004206317a4c8d86accc8258c1ac23ef0ebd18bc33010d7afb43b241802646360b4ab")
        val expectedSig =
            "28e17bc57406650ed78785fd558e7c1861cc4014c900d72b61c03cdbab1039e713b5bb19b556d04d276b46aae9b8a3999ccbac533a1cce00f83cfb83e2beb35ed7329f71ffec04fc2839a9b44e50abd66ea6c3d3bea6705e93e9139ecd0331170db18eba36a85a78bc49a5447260a30ed19d956cb2f8a71f6b19e57fdca43e052d1bb7840bf4c3efb47111f4d77764236d2e013fbf3b2577e4a3e01c9d166a5e890ef96210882e6e88ceca2fe3a2201f4961210d4ec6167f5dfd0e038e4a146f960caecab7d15ba65f6edcf5dbd25f5af543cfb8da4338bdbc872eec3f8e72aa8db679099e70952d3f7176c0b9111bf20ad1390eab1d09a859105816fdf92fbb"
        val privkFile = "../" + Algorithms.get("SHA256_RSA2048")!!.defaultKey.replace("pem", "pk8")
        val k = CryptoHelper.KeyBox.parse(Files.readAllBytes(Paths.get(privkFile))) as PrivateKey
        val encData = CryptoHelper.Signer.rawRsa(k, data)
        assertEquals(expectedSig, Helper.toHexString(encData))
    }

    @Test
    fun rawSignOpenSslTest() {
        val data =
            Helper.fromHexString("0001ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff003031300d0609608648016503040201050004206317a4c8d86accc8258c1ac23ef0ebd18bc33010d7afb43b241802646360b4ab")
        val expectedSig =
            "28e17bc57406650ed78785fd558e7c1861cc4014c900d72b61c03cdbab1039e713b5bb19b556d04d276b46aae9b8a3999ccbac533a1cce00f83cfb83e2beb35ed7329f71ffec04fc2839a9b44e50abd66ea6c3d3bea6705e93e9139ecd0331170db18eba36a85a78bc49a5447260a30ed19d956cb2f8a71f6b19e57fdca43e052d1bb7840bf4c3efb47111f4d77764236d2e013fbf3b2577e4a3e01c9d166a5e890ef96210882e6e88ceca2fe3a2201f4961210d4ec6167f5dfd0e038e4a146f960caecab7d15ba65f6edcf5dbd25f5af543cfb8da4338bdbc872eec3f8e72aa8db679099e70952d3f7176c0b9111bf20ad1390eab1d09a859105816fdf92fbb"
        val sig = CryptoHelper.Signer.rawSignOpenSsl("../" + Algorithms.get("SHA256_RSA2048")!!.defaultKey, data)
        assertEquals(expectedSig, Helper.toHexString(sig))
    }

    @Test
    fun test3() {
        val data =
            Helper.fromHexString("0001ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff003031300d0609608648016503040201050004206317a4c8d86accc8258c1ac23ef0ebd18bc3301033")
        val signature = Signature.getInstance("NONEwithRSA")
        val keyFile = "../" + Algorithms.get("SHA256_RSA2048")!!.defaultKey.replace("pem", "pk8")
        val k = CryptoHelper.KeyBox.parse(Files.readAllBytes(Paths.get(keyFile))) as PrivateKey
        signature.initSign(k)
        signature.update(data)
        println("data size " + data.size)
        println(signature.provider)
        val sig = signature.sign()
        println(sig)
    }

    @Test
    fun testCipher() {
        Security.addProvider(BouncyCastleProvider())
        for (p in Security.getProviders()) {
            println(p.toString())
            for (entry in p.entries) {
                println("\t" + entry.key.toString() + "   ->   " + entry.value)
            }
            println()
        }
    }

    @Test
    fun testKeys() {
        val kp = KeyPairGenerator.getInstance("rsa")
            .apply { this.initialize(2048) }
            .generateKeyPair()
        val pk8Spec = PKCS8EncodedKeySpec(kp.private.encoded) //kp.private.format == PKCS#8
        val x509Spec = X509EncodedKeySpec(kp.public.encoded) //kp.public.format == X.509

        val kf = KeyFactory.getInstance("rsa")
        val privk = kf.generatePrivate(pk8Spec)
        val pubk = kf.generatePublic(x509Spec)
        println(pubk)

        val cipher = Cipher.getInstance("RSA").apply {
            this.init(Cipher.ENCRYPT_MODE, privk)
            this.update("Good".toByteArray())
        }
        val encryptedText = Helper.toHexString(cipher.doFinal())
        println(encryptedText)
    }

    @Test
    fun testRSA() {
//        val r = BigIntegerMath.log2(BigInteger.valueOf(1024), RoundingMode.CEILING)
//        println(r)
//        println(BigInteger.valueOf(1024).mod(BigInteger.valueOf(2)))

        val p = BigInteger.valueOf(3)
        val q = BigInteger.valueOf(7)
        val modulus = p.multiply(q)

        val keyLength = BigIntegerMath.log2(modulus, RoundingMode.CEILING)
        println("keyLength = $keyLength")

        //r = phi(n) = phi(p) * phi(q) = (p - 1)*(q - 1)
        val r = (p.subtract(BigInteger.ONE)).multiply(q - BigInteger.ONE)

        //r ~ e
        //e is released as the public key exponent
        //most commonly e = 2^16 + 1 = 65,537
        val e = BigInteger.valueOf(5)

        //(d * e).mod(r) == 1
        //d is kept as the private key exponent
        val d = e.modInverse(r)

        println("p = $p, q = $q, modulus = $modulus , r = $r, e = $e, d = $d")
        assertEquals(1, d.multiply(e).mod(r).toInt())
        //private key: (modulus, d), d is calculated
        //pub key: (modulus, e) , e is chosen

        val clearMsg = BigInteger.valueOf(10)
        val encMsg = clearMsg.pow(e.toInt()).mod(modulus)
        println("clear: $clearMsg, enc: $encMsg")
    }

    @Test
    fun listAll() {
        CryptoHelper.listAll()
    }

    @Test
    fun signData() {
        val data = KeyUtilTest::class.java.classLoader.getResourceAsStream("data").readAllBytes()
        println(Helper.toHexString(data))
        val privKey = CryptoHelper.KeyBox.parse(
            KeyUtilTest::class.java.classLoader.getResourceAsStream("testkey.pk8").readAllBytes()
        ) as PrivateKey
        println("sha256=" + Helper.toHexString(CryptoHelper.Hasher.sha256(data)))
        val signedHash = CryptoHelper.Signer.sha256rsa(data, privKey)
        println("Signed Hash: " + Helper.toHexString(signedHash))
    }
}

