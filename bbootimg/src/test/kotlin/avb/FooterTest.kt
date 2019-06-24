package avb

import org.apache.commons.codec.binary.Hex
import org.junit.Test

import org.junit.Assert.*
import java.io.ByteArrayInputStream

@ExperimentalUnsignedTypes
class FooterTest {

    @Test
    fun readAVBfooter() {
        val footerBytes = this.javaClass.classLoader.getResourceAsStream("taimen.avbfooter").readBytes()
        ByteArrayInputStream(footerBytes).use {
            it.skip(footerBytes.size - 64L)
            val footer = Footer(it)
            println(footer.toString())
            assertEquals(1U, footer.versionMajor)
            assertEquals(0U, footer.versionMinor)
            assertEquals(512U.toULong(), footer.vbMetaSize)
            assertEquals(28983296U.toULong(), footer.vbMetaOffset)
            assertEquals(28983296U.toULong(), footer.originalImageSize)
        }
    }

    @Test
    fun readInvalidFooterShouldFail() {
        val vbmetaHeaderStr = "4156423000000001000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000c8000000000000000000000000000000c80000000000000000000000000000000000000000000000c800000000000000000000000000000000617662746f6f6c20312e312e3000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
        ByteArrayInputStream(Hex.decodeHex(vbmetaHeaderStr)).use {
            try {
                Footer(it)
                assertEquals("Should never reach here", true, false)
            } catch (e: IllegalArgumentException) {
            }
        }
    }
}