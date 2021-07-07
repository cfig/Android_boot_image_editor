// Copyright 2021 yuyezhong@gmail.com
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package cfig.io

import cfig.io.Struct3.ByteArrayExt.Companion.toCString
import cfig.io.Struct3.ByteArrayExt.Companion.toInt
import cfig.io.Struct3.ByteArrayExt.Companion.toLong
import cfig.io.Struct3.ByteArrayExt.Companion.toShort
import cfig.io.Struct3.ByteArrayExt.Companion.toUInt
import cfig.io.Struct3.ByteArrayExt.Companion.toULong
import cfig.io.Struct3.ByteArrayExt.Companion.toUShort
import cfig.io.Struct3.ByteBufferExt.Companion.appendByteArray
import cfig.io.Struct3.ByteBufferExt.Companion.appendPadding
import cfig.io.Struct3.ByteBufferExt.Companion.appendUByteArray
import cfig.io.Struct3.InputStreamExt.Companion.getByteArray
import cfig.io.Struct3.InputStreamExt.Companion.getCString
import cfig.io.Struct3.InputStreamExt.Companion.getChar
import cfig.io.Struct3.InputStreamExt.Companion.getInt
import cfig.io.Struct3.InputStreamExt.Companion.getLong
import cfig.io.Struct3.InputStreamExt.Companion.getPadding
import cfig.io.Struct3.InputStreamExt.Companion.getShort
import cfig.io.Struct3.InputStreamExt.Companion.getUByteArray
import cfig.io.Struct3.InputStreamExt.Companion.getUInt
import cfig.io.Struct3.InputStreamExt.Companion.getULong
import cfig.io.Struct3.InputStreamExt.Companion.getUShort
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.regex.Pattern
import kotlin.random.Random

class Struct3 {
    private val log = LoggerFactory.getLogger(Struct3::class.java)
    private val formatString: String
    private var byteOrder = ByteOrder.LITTLE_ENDIAN
    private val formats = ArrayList<Array<Any?>>()

    constructor(inFormatString: String) {
        assert(inFormatString.isNotEmpty()) { "FORMAT_STRING must not be empty" }
        formatString = inFormatString
        val m = Pattern.compile("(\\d*)([a-zA-Z])").matcher(formatString)
        when (formatString[0]) {
            '>', '!' -> this.byteOrder = ByteOrder.BIG_ENDIAN
            '@', '=' -> this.byteOrder = ByteOrder.nativeOrder()
            else -> this.byteOrder = ByteOrder.LITTLE_ENDIAN
        }
        while (m.find()) {
            //item[0]: Type, item[1]: multiple
            // if need to expand format items, explode it
            // eg: "4L" will be exploded to "1L 1L 1L 1L", so it's treated as primitive
            // eg: "10x" won't be exploded, it's still "10x", so it's treated as non-primitive
            val typeName: Any = when (m.group(2)) {
                //primitive types
                "x" -> Random //byte 1 (exploded)
                "b" -> Byte //byte 1 (exploded)
                "B" -> UByte //UByte 1 (exploded)
                "s" -> String //string (exploded)
                //zippable types, which need to be exploded with multiple=1
                "c" -> Char
                "h" -> Short //2
                "H" -> UShort //2
                "i", "l" -> Int //4
                "I", "L" -> UInt //4
                "q" -> Long //8
                "Q" -> ULong //8
                else -> throw IllegalArgumentException("type [" + m.group(2) + "] not supported")
            }
            val bPrimitive = m.group(2) in listOf("x", "b", "B", "s")
            val multiple = if (m.group(1).isEmpty()) 1 else Integer.decode(m.group(1))
            if (bPrimitive) {
                formats.add(arrayOf<Any?>(typeName, multiple))
            } else {
                for (i in 0 until multiple) {
                    formats.add(arrayOf<Any?>(typeName, 1))
                }
            }
        }
    }

    private fun getFormatInfo(inCursor: Int): String {
        return ("type=" + formats.get(inCursor)[0] + ", value=" + formats.get(inCursor)[1])
    }

    override fun toString(): String {
        val formatStr = mutableListOf<String>()
        formats.forEach {
            val fs = StringBuilder()
            when (it[0]) {
                Random -> fs.append("x")
                Byte -> fs.append("b")
                UByte -> fs.append("B")
                String -> fs.append("s")
                Char -> fs.append("c")
                Short -> fs.append("h")
                UShort -> fs.append("H")
                Int -> fs.append("i")
                UInt -> fs.append("I")
                Long -> fs.append("q")
                ULong -> fs.append("Q")
                else -> throw IllegalArgumentException("type [" + it[0] + "] not supported")
            }
            fs.append(":" + it[1])
            formatStr.add(fs.toString())
        }
        return "Struct3(formatString='$formatString', byteOrder=$byteOrder, formats=$formatStr)"
    }

    fun calcSize(): Int {
        var ret = 0
        for (format in formats) {
            ret += when (val formatType = format[0]) {
                Random, Byte, UByte, Char, String -> format[1] as Int
                Short, UShort -> 2 * format[1] as Int
                Int, UInt -> 4 * format[1] as Int
                Long, ULong -> 8 * format[1] as Int
                else -> throw IllegalArgumentException("Class [$formatType] not supported")
            }
        }
        return ret
    }

    fun pack(vararg args: Any?): ByteArray {
        if (args.size != this.formats.size) {
            throw IllegalArgumentException("argument size " + args.size +
                    " doesn't match format size " + this.formats.size)
        }
        val bf = ByteBuffer.allocate(this.calcSize())
        bf.order(this.byteOrder)
        for (i in args.indices) {
            val arg = args[i]
            val typeName = formats[i][0]
            val multiple = formats[i][1] as Int

            if (typeName !in arrayOf(Random, Byte, String, UByte)) {
                assert(1 == multiple)
            }

            //x: padding:
            if (Random == typeName) {
                when (arg) {
                    null -> bf.appendPadding(0, multiple)
                    is Byte -> bf.appendPadding(arg, multiple)
                    is Int -> bf.appendPadding(arg.toByte(), multiple)
                    else -> throw IllegalArgumentException("Index[" + i + "] Unsupported arg ["
                            + arg + "] with type [" + formats[i][0] + "]")
                }
                continue
            }

            //c: character
            if (Char == typeName) {
                assert(arg is Char) { "[$arg](${arg!!::class.java}) is NOT Char" }
                if ((arg as Char) !in '\u0000'..'\u00ff') {
                    throw IllegalArgumentException("arg[${arg.code}] exceeds 8-bit bound")
                }
                bf.put(arg.code.toByte())
                continue
            }

            //b: byte array
            if (Byte == typeName) {
                when (arg) {
                    is IntArray -> bf.appendByteArray(arg, multiple)
                    is ByteArray -> bf.appendByteArray(arg, multiple)
                    else -> throw IllegalArgumentException("[$arg](${arg!!::class.java}) is NOT ByteArray/IntArray")
                }
                continue
            }

            //B: UByte array
            if (UByte == typeName) {
                when (arg) {
                    is ByteArray -> bf.appendByteArray(arg, multiple)
                    is UByteArray -> bf.appendUByteArray(arg, multiple)
                    is IntArray -> bf.appendUByteArray(arg, multiple)
                    else -> throw IllegalArgumentException("[$arg](${arg!!::class.java}) is NOT ByteArray/IntArray")
                }
                continue
            }

            //s: String
            if (String == typeName) {
                assert(arg != null) { "arg can not be NULL for String, formatString=$formatString, ${getFormatInfo(i)}" }
                assert(arg is String) { "[$arg](${arg!!::class.java}) is NOT String, ${getFormatInfo(i)}" }
                bf.appendByteArray((arg as String).toByteArray(), multiple)
                continue
            }

            //h: Short
            if (Short == typeName) {
                when (arg) {
                    is Int -> {
                        assert(arg in Short.MIN_VALUE..Short.MAX_VALUE) { "[$arg] is truncated as type Short.class" }
                        bf.putShort(arg.toShort())
                    }
                    is Short -> bf.putShort(arg) //instance Short
                    else -> throw IllegalArgumentException("[$arg](${arg!!::class.java}) is NOT Short/Int")
                }
                continue
            }

            //H: UShort
            if (UShort == typeName) {
                assert(arg is UShort || arg is UInt || arg is Int) { "[$arg](${arg!!::class.java}) is NOT UShort/UInt/Int" }
                when (arg) {
                    is Int -> {
                        assert(arg >= UShort.MIN_VALUE.toInt() && arg <= UShort.MAX_VALUE.toInt()) { "[$arg] is truncated as type UShort" }
                        bf.putShort(arg.toShort())
                    }
                    is UInt -> {
                        assert(arg >= UShort.MIN_VALUE && arg <= UShort.MAX_VALUE) { "[$arg] is truncated as type UShort" }
                        bf.putShort(arg.toShort())
                    }
                    is UShort -> bf.putShort(arg.toShort())
                }
                continue
            }

            //i, l: Int
            if (Int == typeName) {
                assert(arg is Int) { "[$arg](${arg!!::class.java}) is NOT Int" }
                bf.putInt(arg as Int)
                continue
            }

            //I, L: UInt
            if (UInt == typeName) {
                when (arg) {
                    is Int -> {
                        assert(arg >= 0) { "[$arg] is invalid as type UInt" }
                        bf.putInt(arg)
                    }
                    is UInt -> bf.putInt(arg.toInt())
                    is Long -> {
                        assert(arg >= 0) { "[$arg] is invalid as type UInt" }
                        bf.putInt(arg.toInt())
                    }
                    else -> throw IllegalArgumentException("[$arg](${arg!!::class.java}) is NOT UInt/Int/Long")
                }
                continue
            }

            //q: Long
            if (Long == typeName) {
                when (arg) {
                    is Long -> bf.putLong(arg)
                    is Int -> bf.putLong(arg.toLong())
                    else -> throw IllegalArgumentException("[$arg](${arg!!::class.java}) is NOT Long/Int")
                }
                continue
            }

            //Q: ULong
            if (ULong == typeName) {
                when (arg) {
                    is Int -> {
                        assert(arg >= 0) { "[$arg] is invalid as type ULong" }
                        bf.putLong(arg.toLong())
                    }
                    is Long -> {
                        assert(arg >= 0) { "[$arg] is invalid as type ULong" }
                        bf.putLong(arg)
                    }
                    is ULong -> bf.putLong(arg.toLong())
                    else -> throw IllegalArgumentException("[$arg](${arg!!::class.java}) is NOT Int/Long/ULong")
                }
                continue
            }

            throw IllegalArgumentException("unrecognized format $typeName")
        }
        return bf.array()
    }

    @Throws(IOException::class)
    fun unpack(iS: InputStream): List<*> {
        val ret = ArrayList<Any>()
        for (format in this.formats) {
            when (format[0]) {
                Random -> ret.add(iS.getPadding(format[1] as Int)) //return padding byte
                Byte -> ret.add(iS.getByteArray(format[1] as Int)) //b: byte array
                UByte -> ret.add(iS.getUByteArray(format[1] as Int)) //B: ubyte array
                Char -> ret.add(iS.getChar()) //char: 1
                String -> ret.add(iS.getCString(format[1] as Int)) //c string
                Short -> ret.add(iS.getShort(this.byteOrder)) //h: short
                UShort -> ret.add(iS.getUShort(this.byteOrder)) //H: UShort
                Int -> ret.add(iS.getInt(this.byteOrder)) //i, l: Int
                UInt -> ret.add(iS.getUInt(this.byteOrder)) //I, L: UInt
                Long -> ret.add(iS.getLong(this.byteOrder)) //q: Long
                ULong -> ret.add(iS.getULong(this.byteOrder)) //Q: ULong
                else -> throw IllegalArgumentException("Class [" + format[0] + "] not supported")
            }//end-of-when
        }//end-of-for
        return ret
    }

    class ByteBufferExt {
        companion object {
            private val log = LoggerFactory.getLogger(ByteBufferExt::class.java)

            fun ByteBuffer.appendPadding(b: Byte, bufSize: Int) {
                when {
                    bufSize == 0 -> {
                        log.debug("paddingSize is zero, perfect match")
                        return
                    }
                    bufSize < 0 -> {
                        throw IllegalArgumentException("illegal padding size: $bufSize")
                    }
                    else -> {
                        log.debug("paddingSize $bufSize")
                    }
                }
                val padding = ByteArray(bufSize)
                Arrays.fill(padding, b)
                this.put(padding)
            }

            fun ByteBuffer.appendByteArray(inIntArray: IntArray, bufSize: Int) {
                val arg2 = mutableListOf<Byte>()
                inIntArray.toMutableList().mapTo(arg2, {
                    if (it in Byte.MIN_VALUE..Byte.MAX_VALUE)
                        it.toByte()
                    else
                        throw IllegalArgumentException("$it is not valid Byte")
                })
                appendByteArray(arg2.toByteArray(), bufSize)
            }

            fun ByteBuffer.appendByteArray(inByteArray: ByteArray, bufSize: Int) {
                val paddingSize = bufSize - inByteArray.size
                if (paddingSize < 0) throw IllegalArgumentException("arg length [${inByteArray.size}] exceeds limit: $bufSize")
                //data
                this.put(inByteArray)
                //padding
                this.appendPadding(0.toByte(), paddingSize)
                log.debug("paddingSize $paddingSize")
            }

            fun ByteBuffer.appendUByteArray(inIntArray: IntArray, bufSize: Int) {
                val arg2 = mutableListOf<UByte>()
                inIntArray.toMutableList().mapTo(arg2, {
                    if (it in UByte.MIN_VALUE.toInt()..UByte.MAX_VALUE.toInt())
                        it.toUByte()
                    else
                        throw IllegalArgumentException("$it is not valid Byte")
                })
                appendUByteArray(arg2.toUByteArray(), bufSize)
            }

            fun ByteBuffer.appendUByteArray(inUByteArray: UByteArray, bufSize: Int) {
                val bl = mutableListOf<Byte>()
                inUByteArray.toMutableList().mapTo(bl, { it.toByte() })
                this.appendByteArray(bl.toByteArray(), bufSize)
            }
        }
    }

    class InputStreamExt {
        companion object {
            fun InputStream.getChar(): Char {
                val data = ByteArray(Byte.SIZE_BYTES)
                assert(Byte.SIZE_BYTES == this.read(data))
                return data[0].toInt().toChar()
            }

            fun InputStream.getShort(inByteOrder: ByteOrder): Short {
                val data = ByteArray(Short.SIZE_BYTES)
                assert(Short.SIZE_BYTES == this.read(data))
                return data.toShort(inByteOrder)
            }

            fun InputStream.getInt(inByteOrder: ByteOrder): Int {
                val data = ByteArray(Int.SIZE_BYTES)
                assert(Int.SIZE_BYTES == this.read(data))
                return data.toInt(inByteOrder)
            }

            fun InputStream.getLong(inByteOrder: ByteOrder): Long {
                val data = ByteArray(Long.SIZE_BYTES)
                assert(Long.SIZE_BYTES == this.read(data))
                return data.toLong(inByteOrder)
            }

            fun InputStream.getUShort(inByteOrder: ByteOrder): UShort {
                val data = ByteArray(UShort.SIZE_BYTES)
                assert(UShort.SIZE_BYTES == this.read(data))
                return data.toUShort(inByteOrder)
            }

            fun InputStream.getUInt(inByteOrder: ByteOrder): UInt {
                val data = ByteArray(UInt.SIZE_BYTES)
                assert(UInt.SIZE_BYTES == this.read(data))
                return data.toUInt(inByteOrder)
            }

            fun InputStream.getULong(inByteOrder: ByteOrder): ULong {
                val data = ByteArray(ULong.SIZE_BYTES)
                assert(ULong.SIZE_BYTES == this.read(data))
                return data.toULong(inByteOrder)
            }

            fun InputStream.getByteArray(inSize: Int): ByteArray {
                val data = ByteArray(inSize)
                assert(inSize == this.read(data))
                return data
            }

            fun InputStream.getUByteArray(inSize: Int): UByteArray {
                val data = ByteArray(inSize)
                assert(inSize == this.read(data))
                val innerData2 = mutableListOf<UByte>()
                data.toMutableList().mapTo(innerData2, { it.toUByte() })
                return innerData2.toUByteArray()
            }

            fun InputStream.getCString(inSize: Int): String {
                val data = ByteArray(inSize)
                assert(inSize == this.read(data))
                return data.toCString()
            }

            fun InputStream.getPadding(inSize: Int): Byte {
                val data = ByteArray(Byte.SIZE_BYTES)
                assert(Byte.SIZE_BYTES == this.read(data)) //sample the 1st byte
                val skipped = this.skip(inSize.toLong() - Byte.SIZE_BYTES)//skip remaining to save memory
                assert(inSize.toLong() - Byte.SIZE_BYTES == skipped)
                return data[0]
            }
        }
    }

    class ByteArrayExt {
        companion object {
            fun ByteArray.toShort(inByteOrder: ByteOrder): Short {
                val typeSize = Short.SIZE_BYTES / Byte.SIZE_BYTES
                assert(typeSize == this.size) { "Short must have $typeSize bytes" }
                var ret: Short
                ByteBuffer.allocate(typeSize).let {
                    it.order(inByteOrder)
                    it.put(this)
                    it.flip()
                    ret = it.getShort()
                }
                return ret
            }

            fun ByteArray.toInt(inByteOrder: ByteOrder): Int {
                val typeSize = Int.SIZE_BYTES / Byte.SIZE_BYTES
                assert(typeSize == this.size) { "Int must have $typeSize bytes" }
                var ret: Int
                ByteBuffer.allocate(typeSize).let {
                    it.order(inByteOrder)
                    it.put(this)
                    it.flip()
                    ret = it.getInt()
                }
                return ret
            }

            fun ByteArray.toLong(inByteOrder: ByteOrder): Long {
                val typeSize = Long.SIZE_BYTES / Byte.SIZE_BYTES
                assert(typeSize == this.size) { "Long must have $typeSize bytes" }
                var ret: Long
                ByteBuffer.allocate(typeSize).let {
                    it.order(inByteOrder)
                    it.put(this)
                    it.flip()
                    ret = it.getLong()
                }
                return ret
            }

            fun ByteArray.toUShort(inByteOrder: ByteOrder): UShort {
                val typeSize = UShort.SIZE_BYTES / Byte.SIZE_BYTES
                assert(typeSize == this.size) { "UShort must have $typeSize bytes" }
                var ret: UShort
                ByteBuffer.allocate(typeSize).let {
                    it.order(inByteOrder)
                    it.put(this)
                    it.flip()
                    ret = it.getShort().toUShort()
                }
                return ret
            }

            fun ByteArray.toUInt(inByteOrder: ByteOrder): UInt {
                val typeSize = UInt.SIZE_BYTES / Byte.SIZE_BYTES
                assert(typeSize == this.size) { "UInt must have $typeSize bytes" }
                var ret: UInt
                ByteBuffer.allocate(typeSize).let {
                    it.order(inByteOrder)
                    it.put(this)
                    it.flip()
                    ret = it.getInt().toUInt()
                }
                return ret
            }

            fun ByteArray.toULong(inByteOrder: ByteOrder): ULong {
                val typeSize = ULong.SIZE_BYTES / Byte.SIZE_BYTES
                assert(typeSize == this.size) { "ULong must have $typeSize bytes" }
                var ret: ULong
                ByteBuffer.allocate(typeSize).let {
                    it.order(inByteOrder)
                    it.put(this)
                    it.flip()
                    ret = it.getLong().toULong()
                }
                return ret
            }

            //similar to this.toString(StandardCharsets.UTF_8).replace("${Character.MIN_VALUE}", "")
            // not Deprecated for now, "1.3.41 experimental api: ByteArray.decodeToString()") is a little different
            fun ByteArray.toCString(): String {
                val str = this.toString(StandardCharsets.UTF_8)
                val nullPos = str.indexOf(Character.MIN_VALUE)
                return if (nullPos >= 0) {
                    str.substring(0, nullPos)
                } else {
                    str
                }
            }
        }
    }
}
