package cfig.io

import cfig.Helper
import org.junit.Assert
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStream
import java.net.URLStreamHandler
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import java.util.regex.Pattern

class Struct3 {
    private val log = LoggerFactory.getLogger(Struct3::class.java)
    private val formatString: String
    private var byteOrder = ByteOrder.LITTLE_ENDIAN
    private val formats = ArrayList<Array<Any?>>()

    enum class Type {
        Padding,
    }

    constructor(inFormatString: String) {
        formatString = inFormatString
        val m = Pattern.compile("(\\d*)([a-zA-Z])").matcher(formatString)

        if (formatString.startsWith(">") || formatString.startsWith("!")) {
            this.byteOrder = ByteOrder.BIG_ENDIAN
            log.debug("Parsing BIG_ENDIAN format: $formatString")
        } else if (formatString.startsWith("@") || formatString.startsWith("=")) {
            this.byteOrder = ByteOrder.nativeOrder()
            log.debug("Parsing native ENDIAN format: $formatString")
        } else {
            log.debug("Parsing LITTLE_ENDIAN format: $formatString")
        }

        while (m.find()) {
            var bExploded = false
            val multiple = if (m.group(1).isEmpty()) 1 else Integer.decode(m.group(1))
            //item[0]: Type, item[1]: multiple
            // if need to expand format items, explode it
            // eg: "4L" will be exploded to "1L 1L 1L 1L"
            // eg: "10x" won't be exploded, it's still "10x"
            val item = arrayOfNulls<Any?>(2)

            when (m.group(2)) {
                //exploded types
                "x" -> {//byte 1
                    item[0] = Type.Padding
                    bExploded = true
                }
                "b" -> {//byte 1
                    item[0] = Byte
                    bExploded = true
                }
                "B" -> {//UByte 1
                    item[0] = UByte
                    bExploded = true
                }
                "s" -> {//string
                    item[0] = String
                    bExploded = true
                }
                //combo types, which need to be exploded with multiple=1
                "c" -> {//char 1
                    item[0] = Char
                    bExploded = false
                }
                "h" -> {//2
                    item[0] = Short
                }
                "H" -> {//2
                    item[0] = UShort
                }
                "i", "l" -> {//4
                    item[0] = Int
                }
                "I", "L" -> {//4
                    item[0] = UInt
                }
                "q" -> {//8
                    item[0] = Long
                }
                "Q" -> {//8
                    item[0] = ULong
                }
                else -> {
                    throw IllegalArgumentException("type [" + m.group(2) + "] not supported")
                }
            }
            if (bExploded) {
                item[1] = multiple
                formats.add(item)
            } else {
                item[1] = 1
                for (i in 0 until multiple) {
                    formats.add(item)
                }
            }
        }
    }

    private fun getFormatInfo(inCursor: Int): String {
        return ("type=" + formats.get(inCursor)[0] + ", value=" + formats.get(inCursor)[1])
    }

    fun calcSize(): Int? {
        var ret = 0
        for (format in formats) {
            when (format[0]) {
                Byte, UByte, Char, String, Type.Padding -> {
                    ret += format[1] as Int
                }
                Short, UShort -> {
                    ret += 2 * format[1] as Int
                }
                Int, UInt -> {
                    ret += 4 * format[1] as Int
                }
                Long, ULong -> {
                    ret += 8 * format[1] as Int
                }
                else -> {
                    throw IllegalArgumentException("Class [" + format[0] + "] not supported")
                }
            }
        }
        return ret
    }

    fun pack(vararg args: Any?): ByteArray {
        if (args.size != this.formats.size) {
            throw IllegalArgumentException("argument size " + args.size +
                    " doesn't match format size " + this.formats.size)
        } else {
            log.debug("byte buffer size: " + this.calcSize()!!)
        }
        val bf = ByteBuffer.allocate(this.calcSize()!!)
        bf.order(this.byteOrder)
        var formatCursor = -1 //which format item to handle
        for (i in args.indices) {
            formatCursor++
            val arg = args[i]
            val format2 = formats[i][0]
            val size = formats[i][1] as Int

            //x: padding:
            // arg == null:
            // arg is Byte.class
            // arg is Integer.class
            if (Type.Padding == format2) {
                val b = ByteArray(size)
                when (arg) {
                    null -> Arrays.fill(b, 0.toByte())
                    is Byte -> Arrays.fill(b, arg)
                    is Int -> Arrays.fill(b, arg.toByte())
                    else -> throw IllegalArgumentException("Index[" + i + "] Unsupported arg ["
                            + arg + "] with type [" + formats[i][0] + "]")
                }
                bf.put(b)
                continue
            }

            //c: character
            if (Char == format2) {
                Assert.assertEquals(1, size.toLong())
                Assert.assertTrue("[$arg](${arg!!::class.java}) is NOT instance of Character.class",
                        arg is Char)
                bf.put(getLowerByte(arg as Char))
                continue
            }

            //b: byte array
            if (Byte == format2) {
                Assert.assertTrue("[$arg](${arg!!::class.java}) is NOT instance of ByteArray/IntArray",
                        arg is ByteArray || arg is IntArray)
                val argInternal = if (arg is IntArray) {
                    val arg2: MutableList<Byte> = mutableListOf()
                    for (item in arg) {
                        Assert.assertTrue("$item is not valid Byte",
                                item in Byte.MIN_VALUE..Byte.MAX_VALUE)
                        arg2.add(item.toByte())
                    }
                    arg2.toByteArray()
                } else {
                    arg as ByteArray
                }

                val paddingSize = size - argInternal.size
                Assert.assertTrue("argument size overflow: " + argInternal.size + " > " + size,
                        paddingSize >= 0)
                bf.put(argInternal)
                if (paddingSize > 0) {
                    val padBytes = ByteArray(paddingSize)
                    Arrays.fill(padBytes, 0.toByte())
                    bf.put(padBytes)
                    log.debug("paddingSize $paddingSize")
                } else {
                    log.debug("paddingSize is zero, perfect match")
                }
                continue
            }

            //B: UByte array
            if (UByte == format2) {
                Assert.assertTrue("[$arg](${arg!!::class.java}) is NOT instance of ByteArray/IntArray",
                        arg is ByteArray || arg is IntArray || arg is UByteArray)
                val argInternal = if (arg is IntArray) {
                    var arg2: MutableList<Byte> = mutableListOf()
                    for (item in arg) {
                        Assert.assertTrue("$item is not valid UByte",
                                item in UByte.MIN_VALUE.toInt()..UByte.MAX_VALUE.toInt())
                        arg2.add(item.toByte())
                    }
                    arg2.toByteArray()
                } else if (arg is UByteArray) {
                    arg as ByteArray
                } else {
                    arg as ByteArray
                }

                val paddingSize = size - argInternal.size
                Assert.assertTrue("argument size overflow: " + argInternal.size + " > " + size,
                        paddingSize >= 0)
                bf.put(argInternal)
                if (paddingSize > 0) {
                    val padBytes = ByteArray(paddingSize)
                    Arrays.fill(padBytes, 0.toByte())
                    bf.put(padBytes)
                    log.debug("paddingSize $paddingSize")
                } else {
                    log.debug("paddingSize is zero, perfect match")
                }
                continue
            }

            //h: Short
            if (Short == format2) {
                Assert.assertEquals(1, size.toLong())
                Assert.assertTrue("[$arg](${arg!!::class.java}) is NOT instance of Short/Int",
                        arg is Short || arg is Int)
                when (arg) {
                    is Int -> {
                        Assert.assertTrue("[$arg] is truncated as type Short.class",
                                arg in java.lang.Short.MIN_VALUE..java.lang.Short.MAX_VALUE)
                        bf.putShort(arg.toShort())
                    }
                    is Short -> //instance Short
                        bf.putShort(arg)
                }
                continue
            }

            //H: UShort
            if (UShort == format2) {
                Assert.assertEquals(1, size.toLong())
                Assert.assertTrue("[$arg](${arg!!::class.java}) is NOT instance of UShort/UInt/Int",
                        arg is UShort || arg is UInt || arg is Int)
                when (arg) {
                    is Int -> {
                        Assert.assertFalse("[$arg] is truncated as type UShort",
                                arg < UShort.MIN_VALUE.toInt() || arg > UShort.MAX_VALUE.toInt())
                        bf.putShort(arg.toShort())
                    }
                    is UInt -> {
                        Assert.assertFalse("[$arg] is truncated as type UShort",
                                arg < UShort.MIN_VALUE || arg > UShort.MAX_VALUE)
                        bf.putShort(arg.toShort())
                    }
                    is UShort -> bf.putShort(arg.toShort())
                }
                continue
            }

            //i, l: Int
            if (Int == format2) {
                Assert.assertEquals(1, size.toLong())
                Assert.assertTrue("[$arg](${arg!!::class.java}) is NOT instance of Int", arg is Int)
                bf.putInt(arg as Int)
                continue
            }

            //I, L: UInt
            if (UInt == format2) {
                Assert.assertEquals(1, size.toLong())
                Assert.assertTrue("[$arg](${arg!!::class.java}) is NOT instance of UInt/Int/Long",
                        arg is UInt || arg is Int || arg is Long)
                when (arg) {
                    is Int -> {
                        Assert.assertTrue("[$arg] is invalid as type UInt", arg >= 0)
                        bf.putInt(arg)
                    }
                    is UInt -> bf.putInt(arg.toInt())
                    is Long -> {
                        Assert.assertTrue("[$arg] is invalid as type UInt", arg >= 0)
                        bf.putInt(arg.toInt())
                    }
                    else -> {
                        Assert.fail("program bug")
                    }
                }
                continue
            }

            //q: Long
            if (Long == format2) {
                Assert.assertEquals(1, size.toLong())
                Assert.assertTrue("[$arg](${arg!!::class.java}) is NOT instance of Long/Int",
                        arg is Long || arg is Int)
                when (arg) {
                    is Long -> bf.putLong(arg)
                    is Int -> bf.putLong(arg.toLong())
                }
                continue
            }

            //Q: ULong
            if (ULong == format2) {
                Assert.assertEquals(1, size.toLong())
                Assert.assertTrue("[$arg](${arg!!::class.java}) is NOT instance of Int/Long/ULong",
                        arg is Int || arg is Long || arg is ULong)
                when (arg) {
                    is Int -> {
                        Assert.assertTrue("[$arg] is invalid as type ULong", arg >= 0)
                        bf.putLong(arg.toLong())
                    }
                    is Long -> {
                        Assert.assertTrue("[$arg] is invalid as type ULong", arg >= 0)
                        bf.putLong(arg)
                    }
                    is ULong -> {
                        bf.putLong(arg.toLong())
                    }
                }
                continue
            }

            //s: String
            if (String == format2) {
                Assert.assertNotNull("arg can not be NULL for String, formatString=$formatString, ${getFormatInfo(formatCursor)}", arg)
                Assert.assertTrue("[$arg](${arg!!::class.java}) is NOT instance of String.class, ${getFormatInfo(formatCursor)}",
                        arg is String)
                val paddingSize = size - (arg as String).length
                Assert.assertTrue("argument size overflow: " + arg.length + " > " + size,
                        paddingSize >= 0)
                bf.put(arg.toByteArray())
                if (paddingSize > 0) {
                    val padBytes = ByteArray(paddingSize)
                    Arrays.fill(padBytes, 0.toByte())
                    bf.put(padBytes)
                    log.debug("paddingSize $paddingSize")
                } else {
                    log.debug("paddingSize is zero, perfect match")
                }
                continue
            }

            throw java.lang.IllegalArgumentException("unrecognized format $format2")
        }
        log.debug("Pack Result:" + Helper.toHexString(bf.array()))
        return bf.array()
    }

    @Throws(IOException::class)
    fun unpack(iS: InputStream): List<*> {
        val ret = ArrayList<Any>()
        for (format in this.formats) {
            //x: padding
            //return padding byte
            if (format[0] === Type.Padding) {
                val multip = format[1] as Int
                val data = ByteArray(1)
                iS.read(data)//sample the 1st byte
                val skipped = iS.skip(multip.toLong() - 1)//skip remaining
                Assert.assertEquals(multip.toLong() - 1, skipped)
                ret.add(data[0])
                continue
            }

            //b: byte array
            if (format[0] === Byte) {
                val data = ByteArray(format[1] as Int)
                Assert.assertEquals(format[1] as Int, iS.read(data))
                ret.add(data)
                continue
            }

            //B: ubyte array
            if (format[0] === UByte) {
                val data = ByteArray(format[1] as Int)
                Assert.assertEquals(format[1] as Int, iS.read(data))
                val innerData = UByteArray(data.size)
                for (i in 0 until data.size) {
                    innerData[i] = data[i].toUByte()
                }
                ret.add(innerData)
                continue
            }

            //char: 1
            if (format[0] === Char) {
                val data = ByteArray(format[1] as Int)//now its size is fixed at 1
                Assert.assertEquals(format[1] as Int, iS.read(data))
                ret.add(data[0].toChar())
                continue
            }

            //string
            if (format[0] === String) {
                val data = ByteArray(format[1] as Int)
                Assert.assertEquals(format[1] as Int, iS.read(data))
                ret.add(Helper.toCString(data))
                continue
            }

            //h: short
            if (format[0] === Short) {
                val data = ByteArray(2)
                Assert.assertEquals(2, iS.read(data).toLong())
                ByteBuffer.allocate(2).let {
                    it.order(this.byteOrder)
                    it.put(data)
                    it.flip()
                    ret.add(it.short)
                }
                continue
            }

            //H: UShort
            if (format[0] === UShort) {
                val data = ByteArray(2)
                Assert.assertEquals(2, iS.read(data).toLong())
                ByteBuffer.allocate(2).let {
                    it.order(this.byteOrder)
                    it.put(data)
                    it.flip()
                    ret.add(it.short.toUShort())
                }
                continue
            }

            //i, l: Int
            if (format[0] === Int) {
                val data = ByteArray(4)
                Assert.assertEquals(4, iS.read(data).toLong())
                ByteBuffer.allocate(4).let {
                    it.order(this.byteOrder)
                    it.put(data)
                    it.flip()
                    ret.add(it.int)
                }
                continue
            }

            //I, L: UInt
            if (format[0] === UInt) {
                val data = ByteArray(4)
                Assert.assertEquals(4, iS.read(data).toLong())
                ByteBuffer.allocate(4).let {
                    it.order(this.byteOrder)
                    it.put(data)
                    it.flip()
                    ret.add(it.int.toUInt())
                }
                continue
            }

            //q: Long
            if (format[0] === Long) {
                val data = ByteArray(8)
                Assert.assertEquals(8, iS.read(data).toLong())
                ByteBuffer.allocate(8).let {
                    it.order(this.byteOrder)
                    it.put(data)
                    it.flip()
                    ret.add(it.long)
                }
                continue
            }

            //Q: ULong
            if (format[0] === ULong) {
                val data = ByteArray(8)
                Assert.assertEquals(8, iS.read(data).toLong())
                ByteBuffer.allocate(8).let {
                    it.order(this.byteOrder)
                    it.put(data)
                    it.flip()
                    ret.add(it.long.toULong())
                }
                continue
            }

            throw IllegalArgumentException("Class [" + format[0] + "] not supported")
        }
        return ret
    }

    //get lower 1 byte
    private fun getLowerByte(obj: Char?): Byte {
        val bf2 = ByteBuffer.allocate(Character.SIZE / 8) //aka. 16/8
        bf2.putChar(obj!!)
        bf2.flip()
        bf2.get()
        return bf2.get()
    }
}
