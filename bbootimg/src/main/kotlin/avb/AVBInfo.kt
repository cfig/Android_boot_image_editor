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

package avb

import avb.alg.Algorithms
import avb.blob.AuthBlob
import avb.blob.AuxBlob
import avb.blob.Footer
import avb.blob.Header
import avb.desc.*
import cfig.Avb
import cfig.helper.Helper
import cfig.helper.Helper.Companion.paddingWith
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.commons.codec.binary.Hex
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream

/*
    a wonderfaul base64 encoder/decoder: https://cryptii.com/base64-to-hex
 */
class AVBInfo(
    var header: Header? = null,
    var authBlob: AuthBlob? = null,
    var auxBlob: AuxBlob? = null,
    var footer: Footer? = null,
) {
    fun encode(): ByteArray {
        val alg = Algorithms.get(header!!.algorithm_type)!!
        //3 - whole aux blob
        val newAuxBlob = auxBlob?.encode(alg) ?: byteArrayOf()
        //1 - whole header blob
        val headerBlob = this.header!!.apply {
            auxiliary_data_block_size = newAuxBlob.size.toLong()
            authentication_data_block_size = Helper.round_to_multiple(
                (alg.hash_num_bytes + alg.signature_num_bytes).toLong(), 64
            )

            descriptors_offset = 0
            descriptors_size = auxBlob?.descriptorSize?.toLong() ?: 0

            hash_offset = 0
            hash_size = alg.hash_num_bytes.toLong()

            signature_offset = alg.hash_num_bytes.toLong()
            signature_size = alg.signature_num_bytes.toLong()

            public_key_offset = descriptors_size
            public_key_size = AuxBlob.encodePubKey(alg).size.toLong()

            public_key_metadata_size = auxBlob!!.pubkeyMeta?.pkmd?.size?.toLong() ?: 0L
            public_key_metadata_offset = public_key_offset + public_key_size
            log.info("pkmd size: $public_key_metadata_size, pkmd offset : $public_key_metadata_offset")
        }.encode()
        //2 - auth blob
        val authBlob = AuthBlob.createBlob(headerBlob, newAuxBlob, alg.name)
        val ret = Helper.join(headerBlob, authBlob, newAuxBlob)
        //Helper.dumpToFile("_debug_vbmeta_", ret)
        return ret
    }

    fun encodePadded(): ByteArray {
        return encode().paddingWith(Avb.BLOCK_SIZE.toUInt())
    }

    fun dumpDefault(imageFile: String): AVBInfo {
        val jsonFile = Avb.getJsonFileName(imageFile)
        mapper.writerWithDefaultPrettyPrinter().writeValue(File(jsonFile), this)
        log.info("VBMeta: $imageFile -> $jsonFile")
        return this
    }

    companion object {
        private val log = LoggerFactory.getLogger(AVBInfo::class.java)
        private val mapper = ObjectMapper()

        fun parseFrom(imageFile: String): AVBInfo {
            log.info("parseFrom($imageFile) ...")
            var footer: Footer? = null
            var vbMetaOffset: Long = 0
            // footer
            FileInputStream(imageFile).use { fis ->
                fis.skip(File(imageFile).length() - Footer.SIZE)
                try {
                    footer = Footer(fis)
                    vbMetaOffset = footer!!.vbMetaOffset
                    log.info("$imageFile: $footer")
                } catch (e: IllegalArgumentException) {
                    log.info("image $imageFile has no AVB Footer")
                }
            }
            // header
            val rawHeaderBlob = ByteArray(Header.SIZE).apply {
                FileInputStream(imageFile).use { fis ->
                    fis.skip(vbMetaOffset)
                    fis.read(this)
                }
            }
            val vbMetaHeader = Header(ByteArrayInputStream(rawHeaderBlob))
            log.debug(vbMetaHeader.toString())
            log.debug(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(vbMetaHeader))

            val authBlockOffset = vbMetaOffset + Header.SIZE
            val auxBlockOffset = authBlockOffset + vbMetaHeader.authentication_data_block_size

            val ai = AVBInfo(vbMetaHeader, null, AuxBlob(), footer)
            // Auth blob
            if (vbMetaHeader.authentication_data_block_size > 0) {
                FileInputStream(imageFile).use { fis ->
                    fis.skip(vbMetaOffset)
                    fis.skip(Header.SIZE.toLong())
                    fis.skip(vbMetaHeader.hash_offset)
                    val ba = ByteArray(vbMetaHeader.hash_size.toInt())
                    fis.read(ba)
                    log.debug("Parsed Auth Hash (Header & Aux Blob): " + Hex.encodeHexString(ba))
                    val bb = ByteArray(vbMetaHeader.signature_size.toInt())
                    fis.read(bb)
                    log.debug("Parsed Auth Signature (of hash): " + Hex.encodeHexString(bb))
                    ai.authBlob = AuthBlob()
                    ai.authBlob!!.offset = authBlockOffset
                    ai.authBlob!!.size = vbMetaHeader.authentication_data_block_size
                    ai.authBlob!!.hash = Hex.encodeHexString(ba)
                    ai.authBlob!!.signature = Hex.encodeHexString(bb)
                }
            }
            // aux
            val rawAuxBlob = ByteArray(vbMetaHeader.auxiliary_data_block_size.toInt()).apply {
                FileInputStream(imageFile).use { fis ->
                    fis.skip(auxBlockOffset)
                    fis.read(this)
                }
            }
            // aux - desc
            var descriptors: List<Any>
            if (vbMetaHeader.descriptors_size > 0) {
                ByteArrayInputStream(rawAuxBlob).use { bis ->
                    bis.skip(vbMetaHeader.descriptors_offset)
                    descriptors = UnknownDescriptor.parseDescriptors2(bis, vbMetaHeader.descriptors_size)
                }
                descriptors.forEach {
                    log.debug(it.toString())
                    when (it) {
                        is PropertyDescriptor -> {
                            ai.auxBlob!!.propertyDescriptors.add(it)
                        }
                        is HashDescriptor -> {
                            ai.auxBlob!!.hashDescriptors.add(it)
                        }
                        is KernelCmdlineDescriptor -> {
                            ai.auxBlob!!.kernelCmdlineDescriptors.add(it)
                        }
                        is HashTreeDescriptor -> {
                            ai.auxBlob!!.hashTreeDescriptors.add(it)
                        }
                        is ChainPartitionDescriptor -> {
                            ai.auxBlob!!.chainPartitionDescriptors.add(it)
                        }
                        is UnknownDescriptor -> {
                            ai.auxBlob!!.unknownDescriptors.add(it)
                        }
                        else -> {
                            throw IllegalArgumentException("invalid descriptor: $it")
                        }
                    }
                }
            }
            // aux - pubkey
            if (vbMetaHeader.public_key_size > 0) {
                ai.auxBlob!!.pubkey = AuxBlob.PubKeyInfo()
                ai.auxBlob!!.pubkey!!.offset = vbMetaHeader.public_key_offset
                ai.auxBlob!!.pubkey!!.size = vbMetaHeader.public_key_size

                ByteArrayInputStream(rawAuxBlob).use { bis ->
                    bis.skip(vbMetaHeader.public_key_offset)
                    ai.auxBlob!!.pubkey!!.pubkey = ByteArray(vbMetaHeader.public_key_size.toInt())
                    bis.read(ai.auxBlob!!.pubkey!!.pubkey)
                    log.debug("Parsed Pub Key: " + Hex.encodeHexString(ai.auxBlob!!.pubkey!!.pubkey))
                }
            }
            // aux - pkmd
            if (vbMetaHeader.public_key_metadata_size > 0) {
                ai.auxBlob!!.pubkeyMeta = AuxBlob.PubKeyMetadataInfo()
                ai.auxBlob!!.pubkeyMeta!!.offset = vbMetaHeader.public_key_metadata_offset
                ai.auxBlob!!.pubkeyMeta!!.size = vbMetaHeader.public_key_metadata_size

                ByteArrayInputStream(rawAuxBlob).use { bis ->
                    bis.skip(vbMetaHeader.public_key_metadata_offset)
                    ai.auxBlob!!.pubkeyMeta!!.pkmd = ByteArray(vbMetaHeader.public_key_metadata_size.toInt())
                    bis.read(ai.auxBlob!!.pubkeyMeta!!.pkmd)
                    log.debug("Parsed Pub Key Metadata: " + Helper.toHexString(ai.auxBlob!!.pubkeyMeta!!.pkmd))
                }
            }
            log.debug("vbmeta info of [$imageFile] has been analyzed")
            return ai
        }
    }
}