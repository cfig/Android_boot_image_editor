package cfig.helper

import cfig.helper.OpenSslHelper.Companion.decodePem
import org.junit.Test
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.test.assertTrue

class OpenSslHelperTest {
    private val log = LoggerFactory.getLogger(OpenSslHelperTest::class.java)

    @Test
    fun PKCS1test() {
        //private RSA key
        val rsa = OpenSslHelper.PK1Key.generate(4096).apply {
            val fileName = "1_rsa.key"
            writeTo(fileName)
        }

        //Action-1: private RSA key -> RSA public key(PEM)
        val rsaPubPEM = rsa.getPublicKey(OpenSslHelper.KeyFormat.PEM).apply {
            writeTo("2_rsa_pub.pem.key")
        }
        //Action-3: RSA public key(PEM) -->  RSA public key(DER)
        val decodeFromPem = decodePem(String(rsaPubPEM.data))

        //Action-2: private RSA key -> RSA public key(DER)
        val rsaPubDer = rsa.getPublicKey(OpenSslHelper.KeyFormat.DER).apply {
            writeTo("3_rsa_pub.der.key")
        }

        //check equality: 1,3 == 2
        assertTrue(decodeFromPem.contentEquals(rsaPubDer.data))

        run {
            File("1_rsa.key").delete()
            File("2_rsa_pub.pem.key").delete()
            File("3_rsa_pub.der.key").delete()
        }
    }

    @Test
    fun PKCS8Test() {
        //private RSA key
        val rsa = OpenSslHelper.PK1Key.generate(4096)

        run { //check equality: 8 == 4,5
            val pk8Pub = rsa.getPk8PublicKey()
            val pk8Pub2 = rsa.toPk8(OpenSslHelper.KeyFormat.PEM).getPublicKey()
            assert(pk8Pub.data.contentEquals(pk8Pub2.data))
        }

        run { //check equality: 8 == 4,5
            val pk8Pub = rsa.getPk8PublicKey()
            val action8_11 = decodePem(String(pk8Pub.data))
//            val pk8Pub2 = rsa.toPk8(OpenSslHelper.KeyFormat.PEM).getPublicKey()
//            assert(pk8Pub.data.contentEquals(pk8Pub2.data))
        }

        //check equality: 4,9 == original RSA
        rsa.toPk8(OpenSslHelper.KeyFormat.PEM).let { pk8Pem ->
            val shortConversion = pk8Pem.toPk1()
            assert(shortConversion.data.contentEquals(rsa.data))
        }

        //check equality: 7,10,9 == original RSA
        rsa.toPk8(OpenSslHelper.KeyFormat.DER).let { pk8der ->
            val longConversion = pk8der
                    .transform(OpenSslHelper.KeyFormat.DER, OpenSslHelper.KeyFormat.PEM) //pk8 PEM
                    .toPk1() //pk1 PEM
            assert(longConversion.data.contentEquals(rsa.data))
        }
    }

    @Test
    fun CertTest() {
        //private RSA key
        val rsa = OpenSslHelper.PK1Key.generate(4096)
        val crt = rsa.toV1Cert()
        val jks = crt.toJks()
        //jks.writeTo("good.jks")
        val pfx = OpenSslHelper.Pfx(name = "androiddebugkey", thePassword = "somepassword")
        pfx.generate(rsa, crt)
        val jks2 = pfx.toJks()
    }

    @Test
    fun androidCertTest() {
        //platform.x509.pem: Certificate, PEM
        val crt = OpenSslHelper.Crt(data = this.javaClass.classLoader.getResourceAsStream("platform.x509.pem").readBytes())
        val jks = crt.toJks()
        //jks.writeTo("platform.jks")
        jks.check()
    }

    @Test
    fun androidPk8Test() {
        //platform.pk8: Private Key, PKCS#8, DER encoding
        val pk8rsa = OpenSslHelper.PK8RsaKey(OpenSslHelper.KeyFormat.DER,
                this.javaClass.classLoader.getResourceAsStream("platform.pk8").readBytes())
        val pk1 = pk8rsa
                .transform(OpenSslHelper.KeyFormat.DER, OpenSslHelper.KeyFormat.PEM)
                .toPk1()
        val crt = OpenSslHelper.Crt(data = this.javaClass.classLoader.getResourceAsStream("platform.x509.pem").readBytes())
        val pfx = OpenSslHelper.Pfx(name = "androiddebugkey", thePassword = "somepassword").apply {
            this.generate(pk1, crt)
        }
        pfx.toJks().writeTo("platform.jks")
    }
}
