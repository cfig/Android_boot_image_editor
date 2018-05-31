import cfig.Avb
import cfig.Helper
import cfig.io.Struct
import org.junit.Assert
import org.junit.Test

import org.junit.Assert.*
import java.io.ByteArrayInputStream

class StructTest {
    @Test
    fun constructTest() {
        assertEquals(16, Struct("<2i4b4b").calcsize())
        assertEquals(16, Struct("<Q8b").calcsize())
        assertEquals(2, Struct(">h").calcsize())
        assertEquals(3, Struct(">3s").calcsize())
        assertEquals(4, Struct("!Hh").calcsize())

        try {
            Struct("abcd")
            throw Exception("should not reach here")
        } catch (e: IllegalArgumentException) {
        }
    }

    @Test
    fun integerLE() {
        //int (4B)
        assertTrue(Struct("<2i").pack(1, 7321).contentEquals(Helper.fromHexString("01000000991c0000")))
        val ret = Struct("<2i").unpack(ByteArrayInputStream(Helper.fromHexString("01000000991c0000")))
        assertEquals(2, ret.size)
        assertTrue(ret[0] is Int)
        assertTrue(ret[1] is Int)
        assertEquals(1, ret[0] as Int)
        assertEquals(7321, ret[1] as Int)

        //unsigned int (4B)
        assertTrue(Struct("<I").pack(2L).contentEquals(Helper.fromHexString("02000000")))
        assertTrue(Struct("<I").pack(2).contentEquals(Helper.fromHexString("02000000")))
        //greater than Int.MAX_VALUE
        assertTrue(Struct("<I").pack(2147483748L).contentEquals(Helper.fromHexString("64000080")))
        assertTrue(Struct("<I").pack(2147483748).contentEquals(Helper.fromHexString("64000080")))
        try {
            Struct("<I").pack(-12)
            throw Exception("should not reach here")
        } catch (e: IllegalArgumentException) {
        }

        //negative int
        assertTrue(Struct("<i").pack(-333).contentEquals(Helper.fromHexString("b3feffff")))
    }

    @Test
    fun integerBE() {
        run {
            assertTrue(Struct(">2i").pack(1, 7321).contentEquals(Helper.fromHexString("0000000100001c99")))
            val ret = Struct(">2i").unpack(ByteArrayInputStream(Helper.fromHexString("0000000100001c99")))
            assertEquals(1, ret[0] as Int)
            assertEquals(7321, ret[1] as Int)
        }

        run {
            assertTrue(Struct("!i").pack(-333).contentEquals(Helper.fromHexString("fffffeb3")))
            val ret2 = Struct("!i").unpack(ByteArrayInputStream(Helper.fromHexString("fffffeb3")))
            assertEquals(-333, ret2[0] as Int)
        }
    }

    @Test
    fun byteArrayTest() {
        //byte array
        assertTrue(Struct("<4b").pack(byteArrayOf(-128, 2, 55, 127)).contentEquals(Helper.fromHexString("8002377f")))
        assertTrue(Struct("<4b").pack(intArrayOf(0, 55, 202, 0xff)).contentEquals(Helper.fromHexString("0037caff")))
        try {
            Struct("b").pack(intArrayOf(256))
            throw Exception("should not reach here")
        } catch (e: IllegalArgumentException) {
        }
        try {
            Struct("b").pack(intArrayOf(-1))
            throw Exception("should not reach here")
        } catch (e: IllegalArgumentException) {
        }
    }

    @Test
    fun packCombinedTest() {
        assertTrue(Struct("<2i4b4b").pack(
                1, 7321, byteArrayOf(1, 2, 3, 4), byteArrayOf(200.toByte(), 201.toByte(), 202.toByte(), 203.toByte()))!!
                .contentEquals(Helper.fromHexString("01000000991c000001020304c8c9cacb")))
        assertTrue(Struct("<2i4b4b").pack(
                1, 7321, byteArrayOf(1, 2, 3, 4), intArrayOf(200, 201, 202, 203))!!
                .contentEquals(Helper.fromHexString("01000000991c000001020304c8c9cacb")))
    }

    @Test
    fun paddingTest() {
        assertTrue(Struct("b2x").pack(byteArrayOf(0x13), null).contentEquals(Helper.fromHexString("130000")))
        assertTrue(Struct("b2xi").pack(byteArrayOf(0x13), null, 55).contentEquals(Helper.fromHexString("13000037000000")))
    }

    @Test
    fun stringTest() {
        Struct("5s").pack("Good".toByteArray()).contentEquals(Helper.fromHexString("476f6f6400"))
        Struct("5s1b").pack("Good".toByteArray(), byteArrayOf(13)).contentEquals(Helper.fromHexString("476f6f64000d"))
    }
}
