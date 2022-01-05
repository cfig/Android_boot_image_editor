package cfig.io

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.regex.Pattern

class Struct3(inFormatString: String) {
    private val formatString: String = inFormatString
    private var byteOrder = ByteOrder.LITTLE_ENDIAN
    private val warships = ArrayList<IWarShip<*>>()

    init {
        require(inFormatString.isNotEmpty()) { "FORMAT_STRING must not be empty" }
        val m = Pattern.compile("(\\d*)([a-zA-Z])").matcher(formatString)
        this.byteOrder = when (formatString[0]) {
            '>', '!' -> ByteOrder.BIG_ENDIAN
            '@', '=' -> ByteOrder.nativeOrder()
            else -> ByteOrder.LITTLE_ENDIAN
        }
        while (m.find()) {
            //item[0]: Type, item[1]: multiple
            // if need to expand format items, explode it
            // eg: "4L" will be exploded to "1L 1L 1L 1L", so it's treated as primitive
            // eg: "10x" won't be exploded, it's still "10x", so it's treated as non-primitive
            require(m.group(2).length == 1)
            val marker = m.group(2)[0]
            val count = if (m.group(1).isEmpty()) 1 else Integer.decode(m.group(1))
            warships.addAll(makeWarship(marker, count))
        }
    }

    private fun makeWarship(marker: Char, count: Int): List<IWarShip<*>> {
        return when (marker) {
            //primitive types
            's' -> listOf(StringFleet(count)) //string (exploded)
            'x' -> listOf(PaddingFleet(count)) //byte 1 (exploded)
            'b' -> listOf(ByteFleet(count)) //byte 1 (exploded)
            'B' -> listOf(UByteFleet(count)) //UByte 1 (exploded)
            //zippable types, which need to be exploded with multiple=1
            'c' -> List(count) { CharShip() } //1
            'h' -> List(count) { ShortShip() } //2
            'H' -> List(count) { UShortShip() } //2
            'i', 'l' -> List(count) { IntShip() } //4
            'I', 'L' -> List(count) { UIntShip() } //4
            'q' -> List(count) { LongShip() } //8
            'Q' -> List(count) { ULongShip() } //8
            else -> throw IllegalArgumentException("type [$marker] not supported")
        }
    }

    fun calcSize(): Int {
        return warships.sumOf { it.multiple * it.sz }
    }

    override fun toString(): String {
        val sb = StringBuilder("Struct[$byteOrder]")
        warships.map { it.toString() }.reduce { acc, s -> "$acc$s" }
        return sb.toString()
    }

    @Throws(IllegalArgumentException::class)
    fun pack(vararg args: Any?): ByteArray {
        if (args.size != this.warships.size) {
            throw IllegalArgumentException("argument size " + args.size + " doesn't match format size " + this.warships.size)
        }
        return ByteBuffer.allocate(this.calcSize()).let { bf ->
            bf.order(this.byteOrder)
            args.forEachIndexed { index, arg ->
                warships.get(index).put(bf, arg)
            }
            bf.array()
        }
    }

    @Throws(IOException::class, IllegalArgumentException::class)
    fun unpack(iS: InputStream): List<*> {
        return warships.map { it.get(iS, byteOrder) }
    }

    private interface IWarShip<T> {
        val sz: Int
        var multiple: Int
        val log: Logger
        fun get(stream: InputStream, byteOrder: ByteOrder): T
        fun get(ba: ByteArray, byteOrder: ByteOrder): T
        fun put(bf: ByteBuffer, arg: Any?)
    }

    private interface IBaseShip<T> : IWarShip<T> {
        override var multiple: Int
            get() = 1
            set(@Suppress("UNUSED_PARAMETER") value) {}
    }

    private class ShortShip : IBaseShip<Short> {
        override fun get(stream: InputStream, byteOrder: ByteOrder): Short {
            val data = ByteArray(Short.SIZE_BYTES)
            check(Short.SIZE_BYTES == stream.read(data))
            return get(data, byteOrder)
        }

        override fun get(ba: ByteArray, byteOrder: ByteOrder): Short {
            val typeSize = Short.SIZE_BYTES / Byte.SIZE_BYTES
            check(typeSize == ba.size) { "Short must have $typeSize bytes" }
            return ByteBuffer.allocate(ba.size).let {
                it.order(byteOrder)
                it.put(ba)
                it.flip()
                it.getShort()
            }
        }

        override fun put(bf: ByteBuffer, arg: Any?) {
            //h: Short
            when (arg) {
                is Int -> {
                    require(arg in Short.MIN_VALUE..Short.MAX_VALUE) { "[$arg] is truncated as type Short.class" }
                    bf.putShort(arg.toShort())
                }
                is Short -> bf.putShort(arg) //instance Short
                else -> throw IllegalArgumentException("[$arg](${arg!!::class.java}) is NOT Short/Int")
            }
        }

        override fun toString(): String {
            return "h"
        }

        override val sz: Int = Short.SIZE_BYTES
        override val log: Logger = LoggerFactory.getLogger(ShortShip::class.java)
    }

    private class UShortShip : IBaseShip<UShort> {
        override fun get(stream: InputStream, byteOrder: ByteOrder): UShort {
            val data = ByteArray(UShort.SIZE_BYTES)
            check(UShort.SIZE_BYTES == stream.read(data))
            return get(data, byteOrder)
        }

        override fun get(ba: ByteArray, byteOrder: ByteOrder): UShort {
            val typeSize = UShort.SIZE_BYTES / Byte.SIZE_BYTES
            assert(typeSize == ba.size) { "UShort must have $typeSize bytes" }
            return ByteBuffer.allocate(ba.size).let {
                it.order(byteOrder)
                it.put(ba)
                it.flip()
                it.getShort().toUShort()
            }
        }

        override fun put(bf: ByteBuffer, arg: Any?) {
            require(arg is UShort || arg is UInt || arg is Int) { "[$arg](${arg!!::class.java}) is NOT UShort/UInt/Int" }
            when (arg) {
                is Int -> {
                    require(arg >= UShort.MIN_VALUE.toInt() && arg <= UShort.MAX_VALUE.toInt()) { "[$arg] is truncated as type UShort" }
                    bf.putShort(arg.toShort())
                }
                is UInt -> {
                    require(arg >= UShort.MIN_VALUE && arg <= UShort.MAX_VALUE) { "[$arg] is truncated as type UShort" }
                    bf.putShort(arg.toShort())
                }
                is UShort -> bf.putShort(arg.toShort())
                else -> throw IllegalArgumentException("[$arg](${arg!!::class.java}) is NOT valid for UShort")
            }
        }

        override fun toString(): String {
            return "H"
        }

        override val sz: Int = UShort.SIZE_BYTES
        override val log: Logger = LoggerFactory.getLogger(UShortShip::class.java)
    }

    //i, l: Int
    class IntShip : IBaseShip<Int> {
        override fun get(stream: InputStream, byteOrder: ByteOrder): Int {
            val data = ByteArray(Int.SIZE_BYTES)
            check(Int.SIZE_BYTES == stream.read(data))
            return get(data, byteOrder)
        }

        override fun get(ba: ByteArray, byteOrder: ByteOrder): Int {
            val typeSize = Int.SIZE_BYTES / Byte.SIZE_BYTES
            assert(typeSize == ba.size) { "Int must have $typeSize bytes" }
            return ByteBuffer.allocate(ba.size).let {
                it.order(byteOrder)
                it.put(ba)
                it.flip()
                it.getInt()
            }
        }

        override fun put(bf: ByteBuffer, arg: Any?) {
            require(arg is Int) { "[$arg](${arg!!::class.java}) is NOT Int" }
            bf.putInt(arg)
        }

        override fun toString(): String {
            return "i"
        }

        override val sz: Int = Int.SIZE_BYTES
        override val log: Logger = LoggerFactory.getLogger(IntShip::class.java)
    }

    //I, L: UInt
    class UIntShip : IBaseShip<UInt> {
        override fun get(stream: InputStream, byteOrder: ByteOrder): UInt {
            val data = ByteArray(UInt.SIZE_BYTES)
            check(UInt.SIZE_BYTES == stream.read(data))
            return get(data, byteOrder)
        }

        override fun get(ba: ByteArray, byteOrder: ByteOrder): UInt {
            val typeSize = UInt.SIZE_BYTES / Byte.SIZE_BYTES
            assert(typeSize == ba.size) { "UInt must have $typeSize bytes" }
            return ByteBuffer.allocate(ba.size).let {
                it.order(byteOrder)
                it.put(ba)
                it.flip()
                it.getInt().toUInt()
            }
        }

        override fun put(bf: ByteBuffer, arg: Any?) {
            when (arg) {
                is Int -> {
                    require(arg >= 0) { "[$arg] is invalid as type UInt" }
                    bf.putInt(arg)
                }
                is UInt -> bf.putInt(arg.toInt())
                is Long -> {
                    require(arg >= 0) { "[$arg] is invalid as type UInt" }
                    bf.putInt(arg.toInt())
                }
                else -> throw IllegalArgumentException("[$arg](${arg!!::class.java}) is NOT valid UInt")
            }
        }

        override fun toString(): String {
            return "I"
        }

        override val sz: Int = UInt.SIZE_BYTES
        override val log: Logger = LoggerFactory.getLogger(UIntShip::class.java)
    }

    //q: Long
    private class LongShip : IBaseShip<Long> {
        override fun get(stream: InputStream, byteOrder: ByteOrder): Long {
            val data = ByteArray(Long.SIZE_BYTES)
            check(Long.SIZE_BYTES == stream.read(data))
            return get(data, byteOrder)
        }

        override fun get(ba: ByteArray, byteOrder: ByteOrder): Long {
            val typeSize = Long.SIZE_BYTES / Byte.SIZE_BYTES
            check(typeSize == ba.size) { "Long must have $typeSize bytes" }
            return ByteBuffer.allocate(ba.size).let {
                it.order(byteOrder)
                it.put(ba)
                it.flip()
                it.getLong()
            }
        }

        override fun put(bf: ByteBuffer, arg: Any?) {
            when (arg) {
                is Long -> bf.putLong(arg)
                is Int -> bf.putLong(arg.toLong())
                else -> throw IllegalArgumentException("[$arg](${arg!!::class.java}) is NOT valid Long")
            }
        }

        override fun toString(): String {
            return "q"
        }

        override val sz: Int = Long.SIZE_BYTES
        override val log: Logger = LoggerFactory.getLogger(LongShip::class.java)
    }

    //Q: ULong
    private class ULongShip : IBaseShip<ULong> {
        override fun get(stream: InputStream, byteOrder: ByteOrder): ULong {
            val data = ByteArray(ULong.SIZE_BYTES)
            check(ULong.SIZE_BYTES == stream.read(data))
            return get(data, byteOrder)
        }

        override fun get(ba: ByteArray, byteOrder: ByteOrder): ULong {
            val typeSize = ULong.SIZE_BYTES / Byte.SIZE_BYTES
            assert(typeSize == ba.size) { "ULong must have $typeSize bytes" }
            return ByteBuffer.allocate(ba.size).let {
                it.order(byteOrder)
                it.put(ba)
                it.flip()
                it.getLong().toULong()
            }
        }

        override fun put(bf: ByteBuffer, arg: Any?) {
            when (arg) {
                is Int -> {
                    require(arg >= 0) { "[$arg] is invalid as type ULong" }
                    bf.putLong(arg.toLong())
                }
                is Long -> {
                    require(arg >= 0) { "[$arg] is invalid as type ULong" }
                    bf.putLong(arg)
                }
                is ULong -> bf.putLong(arg.toLong())
                else -> throw IllegalArgumentException("[$arg](${arg!!::class.java}) is NOT valid ULong")
            }
        }

        override fun toString(): String {
            return "Q"
        }

        override val sz: Int = ULong.SIZE_BYTES
        override val log: Logger = LoggerFactory.getLogger(ULongShip::class.java)
    }

    //c: character
    private class CharShip : IBaseShip<Char> {
        override fun get(stream: InputStream, byteOrder: ByteOrder): Char {
            val data = ByteArray(Byte.SIZE_BYTES)
            check(Byte.SIZE_BYTES == stream.read(data))
            return data[0].toInt().toChar()
        }

        override fun get(ba: ByteArray, byteOrder: ByteOrder): Char {
            return ba.get(0).toInt().toChar()
        }

        override fun put(bf: ByteBuffer, arg: Any?) {
            require(arg is Char) { "[$arg](${arg!!::class.java}) is NOT Char" }
            if (arg !in '\u0000'..'\u00ff') {
                throw IllegalArgumentException("arg[${arg.code}] exceeds 8-bit bound")
            }
            bf.put(arg.code.toByte())
        }

        override fun toString(): String {
            return "c"
        }

        override val sz: Int = 1
        override val log: Logger = LoggerFactory.getLogger(CharShip::class.java)
    }

    private interface IBaseFleet<T> : IWarShip<T> {
        fun appendPadding(bf: ByteBuffer, b: Byte, bufSize: Int) {
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
            bf.put(padding)
        }

        @Throws(IllegalArgumentException::class)
        fun appendByteArray(bf: ByteBuffer, inByteArray: ByteArray, bufSize: Int) {
            val paddingSize = bufSize - inByteArray.size
            if (paddingSize < 0) throw IllegalArgumentException("arg length [${inByteArray.size}] exceeds limit: $bufSize")
            //data
            bf.put(inByteArray)
            //padding
            appendPadding(bf, 0.toByte(), paddingSize)
            log.debug("paddingSize $paddingSize")
        }

        fun appendByteArray(bf: ByteBuffer, inIntArray: IntArray, bufSize: Int) {
            val arg2 = mutableListOf<Byte>()
            inIntArray.toMutableList().mapTo(arg2) {
                if (it in Byte.MIN_VALUE..Byte.MAX_VALUE) {
                    it.toByte()
                } else {
                    throw IllegalArgumentException("$it is not valid Byte")
                }
            }
            appendByteArray(bf, arg2.toByteArray(), bufSize)
        }

        fun appendUByteArray(bf: ByteBuffer, inIntArray: IntArray, bufSize: Int) {
            val arg2 = mutableListOf<UByte>()
            inIntArray.toMutableList().mapTo(arg2) {
                if (it in UByte.MIN_VALUE.toInt()..UByte.MAX_VALUE.toInt())
                    it.toUByte()
                else {
                    throw IllegalArgumentException("$it is not valid Byte")
                }
            }
            appendUByteArray(bf, arg2.toUByteArray(), bufSize)
        }

        fun appendUByteArray(bf: ByteBuffer, inUByteArray: UByteArray, bufSize: Int) {
            val bl = mutableListOf<Byte>()
            inUByteArray.toMutableList().mapTo(bl) { it.toByte() }
            appendByteArray(bf, bl.toByteArray(), bufSize)
        }
    }

    //x: padding:
    private class PaddingFleet(override var multiple: Int, override val sz: Int = 1) : IBaseFleet<Byte> {
        override fun get(stream: InputStream, byteOrder: ByteOrder): Byte {
            val data = ByteArray(Byte.SIZE_BYTES)
            check(Byte.SIZE_BYTES == stream.read(data)) //sample the 1st byte
            val skipped = stream.skip(multiple.toLong() - Byte.SIZE_BYTES)//skip remaining to save memory
            check(multiple.toLong() - Byte.SIZE_BYTES == skipped)
            return data[0]
        }

        override fun get(ba: ByteArray, byteOrder: ByteOrder): Byte {
            TODO("Not yet implemented")
        }

        override fun put(bf: ByteBuffer, arg: Any?) {
            when (arg) {
                null -> appendPadding(bf, 0, multiple)
                is Byte -> appendPadding(bf, arg, multiple)
                is Int -> appendPadding(bf, arg.toByte(), multiple)
                else -> throw IllegalArgumentException("Unsupported arg [$arg]")
            }
        }

        override fun toString(): String {
            return "${multiple}x"
        }

        override val log: Logger = LoggerFactory.getLogger(PaddingFleet::class.java)
    }

    //b: byte array
    private class ByteFleet(override var multiple: Int = 0) : IBaseFleet<ByteArray> {
        override fun get(stream: InputStream, byteOrder: ByteOrder): ByteArray {
            val data = ByteArray(multiple)
            check(multiple == stream.read(data))
            return data
        }

        override fun get(ba: ByteArray, byteOrder: ByteOrder): ByteArray {
            TODO("Not yet implemented")
        }

        override fun put(bf: ByteBuffer, arg: Any?) {
            when (arg) {
                is IntArray -> appendByteArray(bf, arg, multiple)
                is ByteArray -> appendByteArray(bf, arg, multiple)
                else -> throw IllegalArgumentException("[$arg](${arg!!::class.java}) is NOT ByteArray/IntArray")
            }
        }

        override fun toString(): String {
            return "${multiple}b"
        }

        override val sz: Int = Byte.SIZE_BYTES
        override val log: Logger = LoggerFactory.getLogger(ByteFleet::class.java)
    }

    //B: UByte array
    private class UByteFleet(override var multiple: Int = 0) : IBaseFleet<UByteArray> {
        override fun get(stream: InputStream, byteOrder: ByteOrder): UByteArray {
            val data = ByteArray(multiple)
            check(multiple == stream.read(data))
            val innerData2 = mutableListOf<UByte>()
            data.toMutableList().mapTo(innerData2) { it.toUByte() }
            return innerData2.toUByteArray()
        }

        override fun put(bf: ByteBuffer, arg: Any?) {
            when (arg) {
                is ByteArray -> appendByteArray(bf, arg, multiple)
                is UByteArray -> appendUByteArray(bf, arg, multiple)
                is IntArray -> appendUByteArray(bf, arg, multiple)
                else -> throw IllegalArgumentException("[$arg](${arg!!::class.java}) is NOT ByteArray/IntArray")
            }
        }

        override fun toString(): String {
            return "${multiple}B"
        }

        override val sz: Int
            get() = UByte.SIZE_BYTES

        override fun get(ba: ByteArray, byteOrder: ByteOrder): UByteArray {
            TODO("Not yet implemented")
        }

        override val log: Logger = LoggerFactory.getLogger(UByteFleet::class.java)
    }

    //s: String
    class StringFleet(override var multiple: Int = 0) : IBaseFleet<String> {
        override fun get(stream: InputStream, byteOrder: ByteOrder): String {
            val data = ByteArray(multiple)
            check(multiple == stream.read(data))
            return get(data, byteOrder)
        }

        override fun get(ba: ByteArray, byteOrder: ByteOrder): String {
            return ba.toString(StandardCharsets.UTF_8).let { str ->
                str.indexOf(Character.MIN_VALUE).let { nullPos ->
                    if (nullPos >= 0) str.substring(0, nullPos) else str
                }
            }
        }

        override fun put(bf: ByteBuffer, arg: Any?) {
            requireNotNull(arg) { "arg can not be NULL for String" }
            require(arg is String) { "[$arg](${arg::class.java}) is NOT String" }
            appendByteArray(bf, arg.toByteArray(), multiple)
        }

        override fun toString(): String {
            return "${multiple}s"
        }

        override val sz: Int = 1
        override val log: Logger = LoggerFactory.getLogger(StringFleet::class.java)
    }
}
