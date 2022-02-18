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

package avb.blob

import avb.alg.Algorithm
import avb.desc.*
import cfig.helper.CryptoHelper
import cfig.helper.Helper
import cc.cfig.io.Struct3
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.bouncycastle.asn1.pkcs.RSAPrivateKey
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Paths

@JsonIgnoreProperties("descriptorSize")
class AuxBlob(
    var pubkey: PubKeyInfo? = null,
    var pubkeyMeta: PubKeyMetadataInfo? = null,
    var propertyDescriptors: MutableList<PropertyDescriptor> = mutableListOf(),
    var hashTreeDescriptors: MutableList<HashTreeDescriptor> = mutableListOf(),
    var hashDescriptors: MutableList<HashDescriptor> = mutableListOf(),
    var kernelCmdlineDescriptors: MutableList<KernelCmdlineDescriptor> = mutableListOf(),
    var chainPartitionDescriptors: MutableList<ChainPartitionDescriptor> = mutableListOf(),
    var unknownDescriptors: MutableList<UnknownDescriptor> = mutableListOf()) {

    val descriptorSize: Int
        get(): Int {
            return this.encodeDescriptors().size
        }

    data class PubKeyInfo(
            var offset: Long = 0L,
            var size: Long = 0L,
            var pubkey: ByteArray = byteArrayOf()
    )

    data class PubKeyMetadataInfo(
            var offset: Long = 0L,
            var size: Long = 0L,
            var pkmd: ByteArray = byteArrayOf()
    )

    private fun encodeDescriptors(): ByteArray {
        return mutableListOf<Descriptor>().let { descList ->
            arrayOf(this.propertyDescriptors,        //tag 0
                    this.hashTreeDescriptors,        //tag 1
                    this.hashDescriptors,            //tag 2
                    this.kernelCmdlineDescriptors,   //tag 3
                    this.chainPartitionDescriptors,  //tag 4
                    this.unknownDescriptors          //tag X
            ).forEach { typedList ->
                typedList.forEach { descList.add(it) }
            }
            var ret = byteArrayOf()
            descList.sortBy { it.sequence }
            descList.forEach { ret = Helper.join(ret, it.encode()) }
            ret
        }
    }

    //encoded_descriptors + encoded_key + pkmd_blob + (padding)
    fun encode(alg: Algorithm): ByteArray {
        //descriptors
        val encodedDesc = this.encodeDescriptors()
        //pubkey
        val encodedKey = encodePubKey(alg)
        if (this.pubkey != null) {
            if (encodedKey.contentEquals(this.pubkey!!.pubkey)) {
                log.info("Using the same key as original vbmeta")
            } else {
                log.warn("Using different key from original vbmeta")
            }
        } else {
            log.info("no pubkey in auxBlob")
        }
        //pkmd
        var encodedPkmd = byteArrayOf()
        if (this.pubkeyMeta != null) {
            encodedPkmd = this.pubkeyMeta!!.pkmd
            log.warn("adding pkmd [size=${this.pubkeyMeta!!.pkmd.size}]...")
        } else {
            log.info("no pubkey metadata in auxBlob")
        }

        val auxSize = Helper.round_to_multiple(
                (encodedDesc.size + encodedKey.size + encodedPkmd.size).toLong(),
                64)
        return Struct3("${auxSize}b").pack(Helper.join(encodedDesc, encodedKey, encodedPkmd))
    }

    fun populateDescriptors(descriptors: List<Descriptor>): AuxBlob {
        descriptors.forEach {
            log.debug(it.toString())
            when (it) {
                is PropertyDescriptor -> {
                    this.propertyDescriptors.add(it)
                }
                is HashDescriptor -> {
                    this.hashDescriptors.add(it)
                }
                is KernelCmdlineDescriptor -> {
                    this.kernelCmdlineDescriptors.add(it)
                }
                is HashTreeDescriptor -> {
                    this.hashTreeDescriptors.add(it)
                }
                is ChainPartitionDescriptor -> {
                    this.chainPartitionDescriptors.add(it)
                }
                is UnknownDescriptor -> {
                    this.unknownDescriptors.add(it)
                }
                else -> {
                    throw IllegalArgumentException("invalid descriptor: $it")
                }
            }
        }
        return this
    }

    companion object {
        fun encodePubKey(alg: Algorithm, key: ByteArray? = null): ByteArray {
            var encodedKey = byteArrayOf()
            if (alg.public_key_num_bytes > 0) {
                var algKey: ByteArray? = key
                if (key == null) {
                    algKey = Files.readAllBytes((Paths.get(alg.defaultKey)))
                }
                val rsa = (CryptoHelper.KeyBox.parse2(algKey!!) as Array<*>)[2] as RSAPrivateKey //BC RSA
                encodedKey = CryptoHelper.KeyBox.encodeRSAkey(rsa)
                assert(alg.public_key_num_bytes == encodedKey.size)
            } else {
                log.info("encodePubKey(): No key to encode for algorithm " + alg.name)
            }
            return encodedKey
        }

        private val log = LoggerFactory.getLogger(AuxBlob::class.java)
    }
}
