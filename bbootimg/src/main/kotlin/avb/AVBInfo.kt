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
import avb.desc.UnknownDescriptor
import cfig.Avb
import cfig.helper.Helper
import cfig.helper.Helper.Companion.paddingWith
import cfig.helper.Helper.DataSrc
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.commons.codec.binary.Hex
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.File

/*
    a wonderful base64 encoder/decoder: https://cryptii.com/base64-to-hex
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
            log.debug("pkmd size: $public_key_metadata_size, pkmd offset : $public_key_metadata_offset")
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

        data class Glance(
            var footer: Footer?,
            var vbMetaOffset: Long
        )

        private fun imageGlance(dataSrc: DataSrc<*>): Glance {
            val ret = Glance(null, 0)
            try {
                ret.footer = Footer(dataSrc.readFully(Pair(-Footer.SIZE.toLong(), Footer.SIZE)))
                ret.vbMetaOffset = ret.footer!!.vbMetaOffset
                log.info("${dataSrc.getName()}: $ret.footer")
            } catch (e: IllegalArgumentException) {
                log.info("image ${dataSrc.getName()} has no AVB Footer")
            }
            return ret
        }

        fun parseFrom(dataSrc: DataSrc<*>): AVBInfo {
            log.info("parseFrom(${dataSrc.getName()}) ...")
            // glance
            val (footer, vbMetaOffset) = imageGlance(dataSrc)
            // header
            val vbMetaHeader = Header(dataSrc.readFully(Pair(vbMetaOffset, Header.SIZE)))
            log.debug(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(vbMetaHeader))

            val atlas = mutableMapOf<String, Pair<Long, Int>>()
            atlas["auth"] =
                Pair(vbMetaOffset + Header.SIZE, vbMetaHeader.authentication_data_block_size.toInt())
            atlas["auth.hash"] =
                Pair(atlas["auth"]!!.first + vbMetaHeader.hash_offset, vbMetaHeader.hash_size.toInt())
            atlas["auth.sig"] =
                Pair(atlas["auth.hash"]!!.first + atlas["auth.hash"]!!.second, vbMetaHeader.signature_size.toInt())
            atlas["aux"] =
                Pair(atlas["auth"]!!.first + atlas["auth"]!!.second, vbMetaHeader.auxiliary_data_block_size.toInt())

            val ai = AVBInfo(vbMetaHeader, null, AuxBlob(), footer)
            // Auth blob
            if (vbMetaHeader.authentication_data_block_size > 0) {
                val ba = dataSrc.readFully(atlas["auth.hash"]!!)
                log.debug("Parsed Auth Hash (Header & Aux Blob): " + Hex.encodeHexString(ba))
                val bb = dataSrc.readFully(atlas["auth.sig"]!!)
                log.debug("Parsed Auth Signature (of hash): " + Hex.encodeHexString(bb))
                ai.authBlob = AuthBlob()
                ai.authBlob!!.offset = atlas["auth"]!!.first
                ai.authBlob!!.size = atlas["auth"]!!.second.toLong()
                ai.authBlob!!.hash = Hex.encodeHexString(ba)
                ai.authBlob!!.signature = Hex.encodeHexString(bb)
            }
            // aux
            val rawAuxBlob = dataSrc.readFully(atlas["aux"]!!)
            // aux - desc
            if (vbMetaHeader.descriptors_size > 0) {
                val descriptors = UnknownDescriptor.parseDescriptors(
                    ByteArrayInputStream(
                        rawAuxBlob.copyOfRange(vbMetaHeader.descriptors_offset.toInt(), rawAuxBlob.size)
                    ),
                    vbMetaHeader.descriptors_size
                )
                ai.auxBlob!!.populateDescriptors(descriptors)
            } else {
                log.warn("no descriptors in AVB aux blob")
            }
            // aux - pubkey
            if (vbMetaHeader.public_key_size > 0) {
                ai.auxBlob!!.pubkey = AuxBlob.PubKeyInfo()
                ai.auxBlob!!.pubkey!!.offset = vbMetaHeader.public_key_offset
                ai.auxBlob!!.pubkey!!.size = vbMetaHeader.public_key_size
                ai.auxBlob!!.pubkey!!.pubkey = rawAuxBlob.copyOfRange(
                    vbMetaHeader.public_key_offset.toInt(),
                    (vbMetaHeader.public_key_offset + vbMetaHeader.public_key_size).toInt()
                )
                log.debug("Parsed Pub Key: " + Hex.encodeHexString(ai.auxBlob!!.pubkey!!.pubkey))
            }
            // aux - pkmd
            if (vbMetaHeader.public_key_metadata_size > 0) {
                ai.auxBlob!!.pubkeyMeta = AuxBlob.PubKeyMetadataInfo()
                ai.auxBlob!!.pubkeyMeta!!.offset = vbMetaHeader.public_key_metadata_offset
                ai.auxBlob!!.pubkeyMeta!!.size = vbMetaHeader.public_key_metadata_size
                ai.auxBlob!!.pubkeyMeta!!.pkmd = rawAuxBlob.copyOfRange(
                    vbMetaHeader.public_key_metadata_offset.toInt(),
                    (vbMetaHeader.public_key_metadata_offset + vbMetaHeader.public_key_metadata_size).toInt()
                )
                log.debug("Parsed Pub Key Metadata: " + Helper.toHexString(ai.auxBlob!!.pubkeyMeta!!.pkmd))
            }
            log.debug("vbmeta info of [${dataSrc.getName()}] has been analyzed")
            return ai
        }
    }
}
