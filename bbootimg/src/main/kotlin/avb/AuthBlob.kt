package avb

data class AuthBlob(
        var offset: Long = 0L,
        var size: Long = 0L,
        var hash: String? = null,
        var signature: String? = null)