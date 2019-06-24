package avb.desc

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.commons.codec.binary.Hex
import org.junit.Test

import org.junit.Assert.*
import java.io.ByteArrayInputStream

@ExperimentalUnsignedTypes
class HashTreeDescriptorTest {

    @Test
    fun encode() {
        val treeStr1 = "000000000000000100000000000000e000000001000000009d787000000000009d78700000000000013d9000000010000000100000000002000000009eb60000000000000141400073686131000000000000000000000000000000000000000000000000000000000000000600000020000000140000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000073797374656d28f6d60b554d9532bd45874ab0cdcb2219c4f437c9350f484fa189a881878ab609c2b0ad5852fc0f4a2d03ef9d2be5372e2bd1390000"
        val treeStr2 = "000000000000000100000000000000e000000001000000001ec09000000000001ec0900000000000003e2000000010000000100000000002000000001efeb00000000000003ec00073686131000000000000000000000000000000000000000000000000000000000000000600000020000000140000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000076656e646f7228f6d60b554d9532bd45874ab0cdcb2219c4f437c9350f484fa189a881878ab698cea1ea79a3fa7277255355d42f19af3378b0110000"

        val tree1 = HashTreeDescriptor(ByteArrayInputStream(Hex.decodeHex(treeStr1)), 0)
        println(ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(tree1))
        assertEquals(treeStr1, Hex.encodeHexString(tree1.encode()))

        val reDecoded = HashTreeDescriptor(ByteArrayInputStream(tree1.encode()), 0)
        println(ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(reDecoded))

        val tree2 = HashTreeDescriptor(ByteArrayInputStream(Hex.decodeHex(treeStr2)), 0)
        println(ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(tree2))
        assertEquals(treeStr2, Hex.encodeHexString(tree2.encode()))
    }
}
