package avb.desc

abstract class Descriptor(var tag: Long, var num_bytes_following: Long, var sequence: Int = 0) {
    abstract fun encode(): ByteArray
}