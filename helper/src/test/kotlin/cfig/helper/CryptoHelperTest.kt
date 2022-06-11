package cfig.helper

import org.junit.Assert.assertEquals
import java.io.File

class CryptoHelperTest {
    private fun checkRsaPair(privKey: String) {
        val pubKey = "$privKey.pub"
        val priv = CryptoHelperTest::class.java.classLoader.getResource(privKey).file
        val privK = CryptoHelper.KeyBox.parse4(File(priv).readBytes())
        assertEquals(org.bouncycastle.asn1.pkcs.RSAPrivateKey::class, privK.clazz)

        val pub = CryptoHelperTest::class.java.classLoader.getResource(pubKey).file
        val pubK = CryptoHelper.KeyBox.parse4(File(pub).readBytes())
        assertEquals(java.security.interfaces.RSAPublicKey::class, pubK.clazz)

        assertEquals(
            (privK.key as org.bouncycastle.asn1.pkcs.RSAPrivateKey).modulus,
            (pubK.key as java.security.interfaces.RSAPublicKey).modulus
        )

        assertEquals(
            (privK.key as org.bouncycastle.asn1.pkcs.RSAPrivateKey).publicExponent,
            (pubK.key as java.security.interfaces.RSAPublicKey).publicExponent
        )
    }

    //@Test
    fun parsePemRSA() {
        checkRsaPair("pem.rsa/rsa.2048")
        checkRsaPair("pem.rsa/rsa.4096")
        checkRsaPair("pem.rsa/rsa.8192")
    }

    fun geographicalHash() {
        val f = "/home/work/boot/payload.bin"
        val dg = CryptoHelper.Hasher.hash(Dumpling(f), listOf(Pair(0, 1862657060)), "sha-256")
        println(Helper.toHexString(dg))
    }
}
