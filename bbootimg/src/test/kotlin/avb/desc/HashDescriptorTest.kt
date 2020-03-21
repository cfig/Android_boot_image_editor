package avb.desc

import org.apache.commons.codec.binary.Hex
import org.junit.Assert
import org.junit.Test
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream

@OptIn(ExperimentalUnsignedTypes::class)
class HashDescriptorTest {
    private val log = LoggerFactory.getLogger(HashDescriptorTest::class.java)

    @Test
    fun parseHashDescriptor() {
        val descStr = "000000000000000200000000000000b80000000001ba4000736861323536000000000000000000000000000000000000000000000000000000000004000000200000002000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000626f6f74fbfb8e13c8082e0a16582163ad5075668903cc1237c6c007fed69de05957432103ae125531271eeeb83662cbe21543e3025f2d65268fb6b53c8718a90e3b03c7"
        val desc = HashDescriptor(ByteArrayInputStream(Hex.decodeHex(descStr)))
        log.info(desc.toString())
        Assert.assertEquals(descStr, Hex.encodeHexString(desc.encode()))
    }
}
