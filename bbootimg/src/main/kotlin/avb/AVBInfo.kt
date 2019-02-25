package avb

import avb.desc.*
import cfig.Helper

/*
    a wonderfaul base64 encoder/decoder: https://cryptii.com/base64-to-hex
 */
class AVBInfo(var header: Header? = null,
              var authBlob: AuthBlob? = null,
              var auxBlob: AuxBlob? = null,
              var footer: Footer? = null) {
    data class AuthBlob(
            var offset: Long = 0L,
            var size: Long = 0L,
            var hash: String? = null,
            var signature: String? = null)

    data class AuxBlob(
            var pubkey: PubKeyInfo? = null,
            var pubkeyMeta: PubKeyMetadataInfo? = null,
            var propertyDescriptor: MutableList<PropertyDescriptor> = mutableListOf(),
            var hashTreeDescriptor: MutableList<HashTreeDescriptor> = mutableListOf(),
            var hashDescriptors: MutableList<HashDescriptor> = mutableListOf(),
            var kernelCmdlineDescriptor: MutableList<KernelCmdlineDescriptor> = mutableListOf(),
            var chainPartitionDescriptor: MutableList<ChainPartitionDescriptor> = mutableListOf(),
            var unknownDescriptors: MutableList<UnknownDescriptor> = mutableListOf()
    ) {
        data class PubKeyInfo(
                var offset: Long = 0L,
                var size: Long = 0L,
                var pubkey: ByteArray = byteArrayOf()
        )

        data class PubKeyMetadataInfo(
                var offset: Long = 0L,
                var size: Long = 0L
        )

        fun encodeDescriptors(): ByteArray {
            var descList: MutableList<Descriptor> = mutableListOf()
            this.hashTreeDescriptor.forEach { descList.add(it) }
            this.hashDescriptors.forEach { descList.add(it) }
            this.kernelCmdlineDescriptor.forEach { descList.add(it) }
            this.chainPartitionDescriptor.forEach { descList.add(it) }
            this.unknownDescriptors.forEach { descList.add(it) }
            descList.sortBy { it.sequence }
            var ret = byteArrayOf()
            descList.forEach { ret = Helper.join(ret, it.encode()) }
            return ret
        }
    }
}
