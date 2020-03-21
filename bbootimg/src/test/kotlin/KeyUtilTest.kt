import avb.alg.Algorithms
import cfig.KeyUtil
import org.apache.commons.codec.binary.Hex
import org.junit.Assert.*
import org.junit.Test
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Paths

@OptIn(ExperimentalUnsignedTypes::class)
class KeyUtilTest {
    @Test
    fun parseKeys() {
        val keyFile = "../" + Algorithms.get("SHA256_RSA2048")!!.defaultKey
        val k = KeyUtil.parsePk8PrivateKey(Files.readAllBytes(Paths.get(keyFile.replace("pem", "pk8"))))


        val k2 = KeyUtil.parsePemPrivateKey2(FileInputStream(keyFile))
        println(Hex.encodeHexString(k.encoded))
        println(Hex.encodeHexString(k2.encoded))
    }
}
