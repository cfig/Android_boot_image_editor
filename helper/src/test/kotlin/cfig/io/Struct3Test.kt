package cfig.io

import cfig.helper.Helper
import org.junit.Assert
import org.junit.Test
import java.io.ByteArrayInputStream

class Struct3Test {
    private fun constructorTestFun1(inFormatString: String) {
        println(Struct3(inFormatString))
    }

    @Test
    fun constructorTest() {
        constructorTestFun1("3s")
        constructorTestFun1("5x")
        constructorTestFun1("5b")
        constructorTestFun1("5B")

        constructorTestFun1("2c")
        constructorTestFun1("1h")
        constructorTestFun1("1H")
        constructorTestFun1("1i")
        constructorTestFun1("3I")
        constructorTestFun1("3q")
        constructorTestFun1("3Q")
        constructorTestFun1(">2b202x1b19B")
        constructorTestFun1("<2b2x1b3i2q")
    }

    @Test
    fun calcSizeTest() {
        Assert.assertEquals(3, Struct3("3s").calcSize())
        Assert.assertEquals(5, Struct3("5x").calcSize())
        Assert.assertEquals(5, Struct3("5b").calcSize())
        Assert.assertEquals(5, Struct3("5B").calcSize())

        Assert.assertEquals(9, Struct3("9c").calcSize())
        Assert.assertEquals(8, Struct3("2i").calcSize())
        Assert.assertEquals(8, Struct3("2I").calcSize())
        Assert.assertEquals(24, Struct3("3q").calcSize())
        Assert.assertEquals(24, Struct3("3Q").calcSize())
    }

    @Test
    fun toStringTest() {
        println(Struct3("!4s2L2QL11QL4x47sx80x"))
        println(Struct3("@4s2L2QL11QL4x47sx80x"))
    }

    //x
    @Test
    fun paddingTest() {
        Assert.assertEquals("0000000000", Helper.toHexString(Struct3("5x").pack(null)))
        Assert.assertEquals("0000000000", Helper.toHexString(Struct3("5x").pack(0)))
        Assert.assertEquals("0101010101", Helper.toHexString(Struct3("5x").pack(1)))
        Assert.assertEquals("1212121212", Helper.toHexString(Struct3("5x").pack(0x12)))
        //Integer高位被截掉
        Assert.assertEquals("2323232323", Helper.toHexString(Struct3("5x").pack(0x123)))
        // minus 0001_0011 -> 补码 1110 1101，ie. 0xed
        Assert.assertEquals("ededededed", Helper.toHexString(Struct3("5x").pack(-0x13)))
        //0xff
        Assert.assertEquals("ffffffffff", Helper.toHexString(Struct3("5x").pack(-1)))

        try {
            Struct3("5x").pack("bad")
            Assert.assertTrue("should throw exception here", false)
        } catch (e: IllegalArgumentException) {
        }

        //unpack
        Struct3("3x").unpack(ByteArrayInputStream(Helper.fromHexString("000000"))).let {
            Assert.assertEquals(1, it.size)
            Assert.assertEquals(0.toByte(), it[0])
        }
        Struct3("x2xx").unpack(ByteArrayInputStream(Helper.fromHexString("01121210"))).let {
            Assert.assertEquals(3, it.size)
            Assert.assertEquals(0x1.toByte(), it[0])
            Assert.assertEquals(0x12.toByte(), it[1])
            Assert.assertEquals(0x10.toByte(), it[2])
        }
    }

    //c
    @Test
    fun characterTest() {
        //constructor
        Struct3("c")

        //calcSize
        Assert.assertEquals(3, Struct3("3c").calcSize())

        //pack illegal
        try {
            Struct3("c").pack("a")
            Assert.fail("should throw exception here")
        } catch (e: Throwable) {
            Assert.assertTrue(e is AssertionError || e is IllegalArgumentException)
        }

        //pack legal
        Assert.assertEquals(
            "61",
            Helper.toHexString(Struct3("!c").pack('a'))
        )
        Assert.assertEquals(
            "61",
            Helper.toHexString(Struct3("c").pack('a'))
        )
        Assert.assertEquals(
            "616263",
            Helper.toHexString(Struct3("3c").pack('a', 'b', 'c'))
        )

        //unpack
        Struct3("3c").unpack(ByteArrayInputStream(Helper.fromHexString("616263"))).let {
            Assert.assertEquals(3, it.size)
            Assert.assertEquals('a', it[0])
            Assert.assertEquals('b', it[1])
            Assert.assertEquals('c', it[2])
        }
    }

    //b
    @Test
    fun bytesTest() {
        //constructor
        Struct3("b")

        //calcSize
        Assert.assertEquals(3, Struct3("3b").calcSize())

        //pack
        Assert.assertEquals(
            "123456", Helper.toHexString(
                Struct3("3b").pack(byteArrayOf(0x12, 0x34, 0x56))
            )
        )
        Assert.assertEquals(
            "123456", Helper.toHexString(
                Struct3("!3b").pack(byteArrayOf(0x12, 0x34, 0x56))
            )
        )
        Assert.assertEquals(
            "123400", Helper.toHexString(
                Struct3("3b").pack(byteArrayOf(0x12, 0x34))
            )
        )

        //unpack
        Struct3("3b").unpack(ByteArrayInputStream(Helper.fromHexString("123400"))).let {
            Assert.assertEquals(1, it.size)
            Assert.assertEquals("123400", Helper.toHexString(it[0] as ByteArray))
        }
        Struct3("bbb").unpack(ByteArrayInputStream(Helper.fromHexString("123400"))).let {
            Assert.assertEquals(3, it.size)
            Assert.assertEquals("12", Helper.toHexString(it[0] as ByteArray))
            Assert.assertEquals("34", Helper.toHexString(it[1] as ByteArray))
            Assert.assertEquals("00", Helper.toHexString(it[2] as ByteArray))
        }
    }

    //B: UByte array
    @Test
    fun uBytesTest() {
        //constructor
        Struct3("B")

        //calcSize
        Assert.assertEquals(3, Struct3("3B").calcSize())

        //pack
        Assert.assertEquals(
            "123456", Helper.toHexString(
                Struct3("3B").pack(byteArrayOf(0x12, 0x34, 0x56))
            )
        )
        Assert.assertEquals(
            "123456", Helper.toHexString(
                Struct3("!3B").pack(byteArrayOf(0x12, 0x34, 0x56))
            )
        )
        Assert.assertEquals(
            "123400", Helper.toHexString(
                Struct3("3B").pack(byteArrayOf(0x12, 0x34))
            )
        )

        //unpack
        Struct3("3B").unpack(ByteArrayInputStream(Helper.fromHexString("123400"))).let {
            Assert.assertEquals(1, it.size)
            Assert.assertEquals("123400", Helper.toHexString(it[0] as UByteArray))
        }
        Struct3("BBB").unpack(ByteArrayInputStream(Helper.fromHexString("123400"))).let {
            Assert.assertEquals(3, it.size)
            Assert.assertEquals("12", Helper.toHexString(it[0] as UByteArray))
            Assert.assertEquals("34", Helper.toHexString(it[1] as UByteArray))
            Assert.assertEquals("00", Helper.toHexString(it[2] as UByteArray))
        }
    }

    //s
    @Test
    fun stringTest() {
        //constructor
        Struct3("s")

        //calcSize
        Assert.assertEquals(3, Struct3("3s").calcSize())

        //pack
        Struct3("3s").pack("a")
        Struct3("3s").pack("abc")
        try {
            Struct3("3s").pack("abcd")
            Assert.fail("should throw exception here")
        } catch (e: Throwable) {
            Assert.assertTrue(e.toString(), e is AssertionError || e is IllegalArgumentException)
        }

        //unpack
        Struct3("3s").unpack(ByteArrayInputStream(Helper.fromHexString("616263"))).let {
            Assert.assertEquals(1, it.size)
            Assert.assertEquals("abc", it[0])
        }
        Struct3("3s").unpack(ByteArrayInputStream(Helper.fromHexString("610000"))).let {
            Assert.assertEquals(1, it.size)
            Assert.assertEquals("a", it[0])
        }
    }

    //h
    @Test
    fun shortTest() {
        //constructor
        Struct3("h")

        //calcSize
        Assert.assertEquals(6, Struct3("3h").calcSize())

        //pack
        Assert.assertEquals("ff7f", Helper.toHexString(Struct3("h").pack(0x7fff)))
        Assert.assertEquals("0080", Helper.toHexString(Struct3("h").pack(-0x8000)))
        Assert.assertEquals("7fff0000", Helper.toHexString(Struct3(">2h").pack(0x7fff, 0)))

        //unpack
        Struct3(">2h").unpack(ByteArrayInputStream(Helper.fromHexString("7fff0000"))).let {
            Assert.assertEquals(2, it.size)
            Assert.assertEquals(0x7fff.toShort(), it[0])
            Assert.assertEquals(0.toShort(), it[1])
        }
    }

    //H
    @Test
    fun uShortTest() {
        //constructor
        Struct3("H")

        //calcSize
        Assert.assertEquals(6, Struct3("3H").calcSize())

        //pack
        Assert.assertEquals("0100", Helper.toHexString(Struct3("H").pack((1U).toUShort())))
        Assert.assertEquals("0100", Helper.toHexString(Struct3("H").pack(1U)))
        Assert.assertEquals("ffff", Helper.toHexString(Struct3("H").pack(65535U)))
        Assert.assertEquals("ffff", Helper.toHexString(Struct3("H").pack(65535)))
        try {
            Struct3("H").pack(-1)
            Assert.fail("should throw exception here")
        } catch (e: Throwable) {
            Assert.assertTrue(e is AssertionError || e is IllegalArgumentException)
        }
        //unpack
        Struct3("H").unpack(ByteArrayInputStream(Helper.fromHexString("ffff"))).let {
            Assert.assertEquals(1, it.size)
            Assert.assertEquals(65535U.toUShort(), it[0])
        }
    }

    //i, l
    @Test
    fun intTest() {
        //constructor
        Struct3("i")
        Struct3("l")

        //calcSize
        Assert.assertEquals(12, Struct3("3i").calcSize())
        Assert.assertEquals(12, Struct3("3l").calcSize())

        //pack
        Struct3("i").pack(65535 + 1)
        Struct3("i").pack(-1)
        //unpack
        Struct3("i").unpack(ByteArrayInputStream(Helper.fromHexString("00000100"))).let {
            Assert.assertEquals(1, it.size)
            Assert.assertEquals(65536, it[0])
        }
        Struct3("i").unpack(ByteArrayInputStream(Helper.fromHexString("ffffffff"))).let {
            Assert.assertEquals(1, it.size)
            Assert.assertEquals(-1, it[0])
        }
    }

    //I, L
    @Test
    fun uIntTest() {
        //constructor
        Struct3("I")
        Struct3("L")

        //calcSize
        Assert.assertEquals(12, Struct3("3I").calcSize())
        Assert.assertEquals(12, Struct3("3L").calcSize())

        //pack
        Assert.assertEquals(
            "01000000", Helper.toHexString(
                Struct3("I").pack(1U)
            )
        )
        Assert.assertEquals(
            "80000000", Helper.toHexString(
                Struct3(">I").pack(Int.MAX_VALUE.toUInt() + 1U)
            )
        )
        //unpack
        Struct3("I").unpack(ByteArrayInputStream(Helper.fromHexString("01000000"))).let {
            Assert.assertEquals(1, it.size)
            Assert.assertEquals(1U, it[0])
        }
        Struct3(">I").unpack(ByteArrayInputStream(Helper.fromHexString("80000000"))).let {
            Assert.assertEquals(1, it.size)
            Assert.assertEquals(Int.MAX_VALUE.toUInt() + 1U, it[0])
        }
    }

    //q: Long
    @Test
    fun longTest() {
        //constructor
        Struct3("q")

        //calcSize
        Assert.assertEquals(24, Struct3("3q").calcSize())

        //pack
        Assert.assertEquals(
            "8000000000000000", Helper.toHexString(
                Struct3(">q").pack(Long.MIN_VALUE)
            )
        )
        Assert.assertEquals(
            "7fffffffffffffff", Helper.toHexString(
                Struct3(">q").pack(Long.MAX_VALUE)
            )
        )
        Assert.assertEquals(
            "ffffffffffffffff", Helper.toHexString(
                Struct3(">q").pack(-1L)
            )
        )
        //unpack
        Struct3(">q").unpack(ByteArrayInputStream(Helper.fromHexString("8000000000000000"))).let {
            Assert.assertEquals(1, it.size)
            Assert.assertEquals(Long.MIN_VALUE, it[0])
        }
        Struct3(">q").unpack(ByteArrayInputStream(Helper.fromHexString("7fffffffffffffff"))).let {
            Assert.assertEquals(1, it.size)
            Assert.assertEquals(Long.MAX_VALUE, it[0])
        }
        Struct3(">q").unpack(ByteArrayInputStream(Helper.fromHexString("ffffffffffffffff"))).let {
            Assert.assertEquals(1, it.size)
            Assert.assertEquals(-1L, it[0])
        }
    }

    //Q: ULong
    @Test
    fun uLongTest() {
        //constructor
        Struct3("Q")

        //calcSize
        Assert.assertEquals(24, Struct3("3Q").calcSize())

        //pack
        Assert.assertEquals(
            "7fffffffffffffff", Helper.toHexString(
                Struct3(">Q").pack(Long.MAX_VALUE)
            )
        )
        Assert.assertEquals(
            "0000000000000000", Helper.toHexString(
                Struct3(">Q").pack(ULong.MIN_VALUE)
            )
        )
        Assert.assertEquals(
            "ffffffffffffffff", Helper.toHexString(
                Struct3(">Q").pack(ULong.MAX_VALUE)
            )
        )
        try {
            Struct3(">Q").pack(-1L)
        } catch (e: Throwable) {
            Assert.assertTrue(e is AssertionError || e is IllegalArgumentException)
        }
        //unpack
        Struct3(">Q").unpack(ByteArrayInputStream(Helper.fromHexString("7fffffffffffffff"))).let {
            Assert.assertEquals(1, it.size)
            Assert.assertEquals(Long.MAX_VALUE.toULong(), it[0])
        }
        Struct3(">Q").unpack(ByteArrayInputStream(Helper.fromHexString("0000000000000000"))).let {
            Assert.assertEquals(1, it.size)
            Assert.assertEquals(ULong.MIN_VALUE, it[0])
        }
        Struct3(">Q").unpack(ByteArrayInputStream(Helper.fromHexString("ffffffffffffffff"))).let {
            Assert.assertEquals(1, it.size)
            Assert.assertEquals(ULong.MAX_VALUE, it[0])
        }
    }

    @Test
    fun legacyTest() {
        Assert.assertTrue(
            Struct3("<2i4b4b").pack(
                1, 7321, byteArrayOf(1, 2, 3, 4), byteArrayOf(200.toByte(), 201.toByte(), 202.toByte(), 203.toByte())
            )
                .contentEquals(Helper.fromHexString("01000000991c000001020304c8c9cacb"))
        )
        Assert.assertTrue(
            Struct3("<2i4b4B").pack(
                1, 7321, byteArrayOf(1, 2, 3, 4), intArrayOf(200, 201, 202, 203)
            )
                .contentEquals(Helper.fromHexString("01000000991c000001020304c8c9cacb"))
        )

        Assert.assertTrue(Struct3("b2x").pack(byteArrayOf(0x13), null).contentEquals(Helper.fromHexString("130000")))
        Assert.assertTrue(
            Struct3("b2xi").pack(byteArrayOf(0x13), null, 55).contentEquals(Helper.fromHexString("13000037000000"))
        )

        Struct3("5s").pack("Good").contentEquals(Helper.fromHexString("476f6f6400"))
        Struct3("5s1b").pack("Good", byteArrayOf(13)).contentEquals(Helper.fromHexString("476f6f64000d"))
    }


    @Test
    fun legacyIntegerLE() {
        //int (4B)
        Assert.assertTrue(Struct3("<2i").pack(1, 7321).contentEquals(Helper.fromHexString("01000000991c0000")))
        val ret = Struct3("<2i").unpack(ByteArrayInputStream(Helper.fromHexString("01000000991c0000")))
        Assert.assertEquals(2, ret.size)
        Assert.assertTrue(ret[0] is Int)
        Assert.assertTrue(ret[1] is Int)
        Assert.assertEquals(1, ret[0] as Int)
        Assert.assertEquals(7321, ret[1] as Int)

        //unsigned int (4B)
        Assert.assertTrue(Struct3("<I").pack(2L).contentEquals(Helper.fromHexString("02000000")))
        Assert.assertTrue(Struct3("<I").pack(2).contentEquals(Helper.fromHexString("02000000")))
        //greater than Int.MAX_VALUE
        Assert.assertTrue(Struct3("<I").pack(2147483748L).contentEquals(Helper.fromHexString("64000080")))
        Assert.assertTrue(Struct3("<I").pack(2147483748).contentEquals(Helper.fromHexString("64000080")))
        try {
            Struct3("<I").pack(-12)
            throw Exception("should not reach here")
        } catch (e: Throwable) {
            Assert.assertTrue(e is AssertionError || e is IllegalArgumentException)
        }

        //negative int
        Assert.assertTrue(Struct3("<i").pack(-333).contentEquals(Helper.fromHexString("b3feffff")))
    }

    @Test
    fun legacyIntegerBE() {
        run {
            Assert.assertTrue(Struct3(">2i").pack(1, 7321).contentEquals(Helper.fromHexString("0000000100001c99")))
            val ret = Struct3(">2i").unpack(ByteArrayInputStream(Helper.fromHexString("0000000100001c99")))
            Assert.assertEquals(1, ret[0] as Int)
            Assert.assertEquals(7321, ret[1] as Int)
        }

        run {
            Assert.assertTrue(Struct3("!i").pack(-333).contentEquals(Helper.fromHexString("fffffeb3")))
            val ret2 = Struct3("!i").unpack(ByteArrayInputStream(Helper.fromHexString("fffffeb3")))
            Assert.assertEquals(-333, ret2[0] as Int)
        }
    }
}
