package avb.desc

class ChainPartitionDescriptor {
    companion object {
        const val TAG = 4L
        const val RESERVED = 64
        const val SIZE = 28 + RESERVED
        const val FORMAT_STRING = "!2Q3L"
    }
}