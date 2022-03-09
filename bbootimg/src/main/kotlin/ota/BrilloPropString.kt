// Copyright 2022 yuyezhong@gmail.com
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

package cc.cfig.droid.ota

import cc.cfig.io.Struct
import cfig.helper.ZipHelper
import cfig.helper.ZipHelper.Companion.getEntryOffset
import org.apache.commons.compress.archivers.zip.ZipFile
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.IllegalArgumentException
import java.util.*

open class BrilloPropString {
    open val name: String = ""
    open val required: MutableList<String> = mutableListOf()
    open val optional: MutableList<String> = mutableListOf()

    companion object {
        private val log = LoggerFactory.getLogger(BrilloPropString::class.qualifiedName)
        const val metaDataName = "META-INF/com/android/metadata"

        fun Properties.toBrilloString(): String {
            val metaValueList = this.map { "${it.key}=${it.value}\n" }.sorted()
            return metaValueList.reduce { acc, s -> "$acc$s" }
        }

        fun computeAllPropertyStrings(fileName: String, inPropertyStrings: List<BrilloPropString>): Properties {
            return Properties().let { metadata ->
                inPropertyStrings.forEach {
                    metadata[it.name] = it.preCompute(fileName)
                }
                metadata
            }
        }

        fun finalizeAllPropertyFiles(
            fileName: String,
            inPropertyStrings: List<BrilloPropString>,
            preComputed: Properties
        ): Properties {
            val metadata = Properties()
            val zf = ZipFile(fileName)
            inPropertyStrings.forEach {
                metadata[it.name] = it.postCompute(fileName, (preComputed[it.name] as String).length)
            }
            zf.close()
            return metadata
        }

        fun rmMetaData(fileName: String) {
            ZipFile(fileName).use { zf ->
                val metadataEntry = zf.getEntry(metaDataName)
                if (metadataEntry != null) {
                    log.info("$metaDataName exists, needs to be erased")
                    ZipHelper.zipDelete(File(fileName), metaDataName)
                } else {
                    log.info("$metaDataName doesn't exist")
                }
            }
        }
    }

    /*
        pre-compute: with mimiced "metadata"
     */
    fun preCompute(fileName: String): String {
        return this.fromZipFile(fileName, reserveSpace = true)
    }

    /*
        finalize return string with padding spaces
     */
    fun postCompute(fileName: String, reservedLen: Int): String {
        val result = fromZipFile(fileName, reserveSpace = false)
        if (result.length > reservedLen) {
            throw IllegalArgumentException("Insufficient reserved space: reserved=$reservedLen, actual=${result.length}")
        }
        return result + " ".repeat(reservedLen - result.length)
    }

    fun verify(fileName: String, expected: String) {
        log.info("verifying $fileName:${this.name} ...")
        val actual = fromZipFile(fileName, reserveSpace = false)
        if (actual != expected.trim()) {
            throw RuntimeException("Mismatching streaming metadata: [$actual] vs [$expected]")
        } else {
            log.info("Verified $fileName:${this.name} against [$expected]")
        }
    }

    private fun fromZipFile(fileName: String, reserveSpace: Boolean = false): String {
        ZipFile(fileName).use { zf ->
            val token: MutableList<BrilloProp> = computePrivateProps(fileName)
            this.required.forEach {
                token.add(BrilloProp(zf, it))
            }
            this.optional.filter { zf.getEntry(it) != null }.forEach {
                token.add(BrilloProp(zf, it))
            }
            if (reserveSpace) {
                token.add(BrilloProp("metadata", 0L, 0L))
            } else {
                log.info("$metaDataName is " + BrilloProp(zf, metaDataName).toString())
                token.add(BrilloProp(zf, metaDataName))
            }
            val ret = token.map { it.toString() }.reduce { acc, s -> "$acc,$s" }
            log.info("fromZipFile($fileName) = [$ret]")
            return ret
        }
    }

    open fun computePrivateProps(fileName: String): MutableList<BrilloProp> {
        return mutableListOf()
    }
}

open class StreamingBrilloPropString : BrilloPropString() {
    override val name: String = "ota-streaming-property-files"
    override val required: MutableList<String> = mutableListOf("payload.bin", "payload_properties.txt")

    //care_map is available only if dm-verity is enabled
    //compatibility.zip is available only if target supports Treble
    override val optional: MutableList<String> = mutableListOf("care_map.pb", "care_map.txt", "compatibility.zip")
}

class NonAbBrilloPropString : BrilloPropString() {
    override val name: String = "ota-property-files"
}

/*
    AbBrilloPropString will replace StreamingBrilloPropString after P-timeframe
 */
@OptIn(ExperimentalUnsignedTypes::class)
class AbBrilloPropString : StreamingBrilloPropString() {
    override val name: String = "ota-property-files"

    override fun computePrivateProps(fileName: String): MutableList<BrilloProp> {
        ZipFile(fileName).use { zf ->
            val pb = zf.getEntry("payload.bin")
            val headerFormat = Struct("!IQQL")
            val header = headerFormat.unpack(zf.getInputStream(pb))
            val magic = header[0] as UInt
            val manifestSize = header[2] as ULong
            val metaSigSize = header[3] as UInt
            if (0x43724155U != magic) {//'CrAU'
                throw IllegalArgumentException("Invalid magic 0x" + magic.toString(16))
            }
            val metaTotal = headerFormat.calcSize().toULong() + manifestSize + metaSigSize
            if (metaTotal >= pb.size.toUInt()) {
                throw IllegalArgumentException("metadata total size >= payload size")
            }
            return mutableListOf(BrilloProp("payload_metadata.bin", pb.getEntryOffset(), metaTotal.toLong()))
        }
    }
}
