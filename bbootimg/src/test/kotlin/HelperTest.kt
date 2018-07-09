import avb.alg.Algorithms
import cfig.Helper
import org.apache.commons.codec.binary.Hex
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.Security
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

class HelperTest {
    @Test
    fun rawSignTest() {
        val data = Hex.decodeHex("0001ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff003031300d0609608648016503040201050004206317a4c8d86accc8258c1ac23ef0ebd18bc33010d7afb43b241802646360b4ab")
        val expectedSig = "28e17bc57406650ed78785fd558e7c1861cc4014c900d72b61c03cdbab1039e713b5bb19b556d04d276b46aae9b8a3999ccbac533a1cce00f83cfb83e2beb35ed7329f71ffec04fc2839a9b44e50abd66ea6c3d3bea6705e93e9139ecd0331170db18eba36a85a78bc49a5447260a30ed19d956cb2f8a71f6b19e57fdca43e052d1bb7840bf4c3efb47111f4d77764236d2e013fbf3b2577e4a3e01c9d166a5e890ef96210882e6e88ceca2fe3a2201f4961210d4ec6167f5dfd0e038e4a146f960caecab7d15ba65f6edcf5dbd25f5af543cfb8da4338bdbc872eec3f8e72aa8db679099e70952d3f7176c0b9111bf20ad1390eab1d09a859105816fdf92fbb"
        val privkFile = "../" + Algorithms.get("SHA256_RSA2048")!!.defaultKey.replace("pem", "pk8")
        val encData = Helper.rawSign(privkFile, data)
        assertEquals(expectedSig, Hex.encodeHexString(encData))
    }

    @Test
    fun rawSignOpenSslTest() {
        val data = Hex.decodeHex("0001ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff003031300d0609608648016503040201050004206317a4c8d86accc8258c1ac23ef0ebd18bc33010d7afb43b241802646360b4ab")
        val expectedSig = "28e17bc57406650ed78785fd558e7c1861cc4014c900d72b61c03cdbab1039e713b5bb19b556d04d276b46aae9b8a3999ccbac533a1cce00f83cfb83e2beb35ed7329f71ffec04fc2839a9b44e50abd66ea6c3d3bea6705e93e9139ecd0331170db18eba36a85a78bc49a5447260a30ed19d956cb2f8a71f6b19e57fdca43e052d1bb7840bf4c3efb47111f4d77764236d2e013fbf3b2577e4a3e01c9d166a5e890ef96210882e6e88ceca2fe3a2201f4961210d4ec6167f5dfd0e038e4a146f960caecab7d15ba65f6edcf5dbd25f5af543cfb8da4338bdbc872eec3f8e72aa8db679099e70952d3f7176c0b9111bf20ad1390eab1d09a859105816fdf92fbb"

        val sig = Helper.rawSignOpenSsl("../" + Algorithms.get("SHA256_RSA2048")!!.defaultKey, data)
        assertEquals(expectedSig, Hex.encodeHexString(sig))
    }

    @Test
    fun test3() {
        val data = Hex.decodeHex("0001ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff003031300d0609608648016503040201050004206317a4c8d86accc8258c1ac23ef0ebd18bc3301033")
        val signature = Signature.getInstance("NONEwithRSA")
        val k = Helper.readPrivateKey("../" + Algorithms.get("SHA256_RSA2048")!!.defaultKey.replace("pem", "pk8"))
        signature.initSign(k)
        signature.update(data)
        println("data size " + data.size)
        println(signature.provider)
        val sig = signature.sign()
//        assertEquals(expectedSig, Hex.encodeHexString(sig))
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

        val cipher = Cipher.getInstance("RSA").apply {
            this.init(Cipher.ENCRYPT_MODE, privk)
            this.update("Good".toByteArray())
        }
        val encryptedText = Hex.encodeHexString(cipher.doFinal())
        println(encryptedText)
    }
}
