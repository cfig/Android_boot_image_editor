package avb.desc

import org.apache.commons.codec.binary.Hex
import org.junit.Test

import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream

@OptIn(ExperimentalUnsignedTypes::class)
class UnknownDescriptorTest {
    private val log = LoggerFactory.getLogger(UnknownDescriptorTest::class.java)

    @Test
    fun readDescriptors() {
        //output by "xxd -p <file>"
        val descStr = "000000000000000200000000000000b800000000017b9000736861323536" +
                "000000000000000000000000000000000000000000000000000000000004" +
                "000000200000002000000000000000000000000000000000000000000000" +
                "000000000000000000000000000000000000000000000000000000000000" +
                "000000000000000000000000626f6f7428f6d60b554d9532bd45874ab0cd" +
                "cb2219c4f437c9350f484fa189a881878ab6156408cd763ff119635ec9db" +
                "2a9656e220fa1dc27e26e59bd3d85025b412ffc3"
        val descBA = Hex.decodeHex(descStr + descStr)
        val descList = UnknownDescriptor.parseDescriptors(ByteArrayInputStream(descBA), descBA.size.toLong())
        descList.forEach{
            log.info(it.toString())
        }
    }
}
