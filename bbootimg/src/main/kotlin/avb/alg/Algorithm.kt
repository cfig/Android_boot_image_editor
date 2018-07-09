package avb.alg

data class Algorithm(
        val name: String = "NONE",
        val algorithm_type: Int = 0,
        val hash_name: String = "",
        val hash_num_bytes: Int = 0,
        val signature_num_bytes: Int = 0,
        val public_key_num_bytes: Int = 0,
        val padding: ByteArray = byteArrayOf(),
        val defaultKey: String ="")