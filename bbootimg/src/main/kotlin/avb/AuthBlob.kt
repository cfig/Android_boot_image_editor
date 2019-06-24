package avb

@ExperimentalUnsignedTypes
data class AuthBlob(
        var offset: ULong = 0U,
        var size: ULong = 0U,
        var hash: String? = null,
        var signature: String? = null)