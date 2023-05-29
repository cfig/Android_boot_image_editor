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

import cfig.helper.Helper
import cfig.helper.ZipHelper
import chromeos_update_engine.UpdateMetadata
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.util.*
import chromeos_update_engine.UpdateMetadata.InstallOperation.Type
import java.io.ByteArrayInputStream

class DeltaGenerator {
    class ChunkProcessor(
        val name: String,
        val op: UpdateMetadata.InstallOperation,
        val blockSize: Int,
        val partFile: String,
    ) {
        fun ProcessChunk() {
            log.info("ChunkProcessor: $name")
            FileInputStream(partFile).use { fis ->
                val dst0 = op.getDstExtents(0)
                fis.skip(dst0.startBlock * blockSize)
                val data = ByteArray((dst0.numBlocks * blockSize).toInt())
                if (data.size != fis.read(data)) {
                    throw RuntimeException("$name: read size != expected size")
                }
                val bestOp = GenerateBestFullOperation(data)
                if (bestOp[0] as Boolean) {
                    log.info("bestType=" + bestOp[1] as Type + ", bestSize=" + bestOp[2] as Int)
                } else {
                    throw IllegalStateException("GenerateBestFullOperation fail")
                }
            }
        }

        companion object {
            fun GenerateBestFullOperation(inData: ByteArray): Array<Any> {
                val ret: Array<Any> = Array(3) { 0 }
                var bestType: Type = Type.REPLACE
                var bestSize: Int = inData.size
                //buffer MUST be valid
                if (inData.isEmpty()) {
                    ret[0] = false
                    return ret
                }
                //zero
                if (inData.all { it.toInt() == 0 }) {
                    bestType = Type.ZERO
                    log.info("raw=${inData.size}, ZERO")
                    ret[0] = true
                    ret[1] = bestType
                    return ret
                }
                //try xz
                File.createTempFile("pre", "suf").let { tempFile ->
                    tempFile.deleteOnExit()
                    ZipHelper.xz(tempFile.absolutePath, ByteArrayInputStream(inData), "CRC64")
                    log.debug("raw=${inData.size}, xz=" + tempFile.length())
                    if (bestSize > tempFile.length()) {
                        bestType = Type.REPLACE_XZ
                        bestSize = tempFile.length().toInt()
                    }
                }
                //try bz
                File.createTempFile("pre", "suf").let { tempFile ->
                    tempFile.deleteOnExit()
                    ZipHelper.bzip2(tempFile.absolutePath, ByteArrayInputStream(inData))
                    log.debug("raw=${inData.size}, bzip2=" + tempFile.length())
                    if (bestSize > tempFile.length()) {
                        bestType = Type.REPLACE_BZ
                        bestSize = tempFile.length().toInt()
                    }
                }
                ret[0] = true
                ret[1] = bestType
                ret[2] = bestSize
                return ret
            }
        }
    }

    class FullPayloadGenerator {
        fun GenerateOperations(partName: String, partFile: String) {
            val config = Properties().apply {
                put("full_chunk_size", (2 * 1024 * 1024).toInt())
                put("block_size", (4 * 1024).toInt())
            }
            val fullChunkSize = config.get("full_chunk_size") as Int
            val blockSize = config.get("block_size") as Int
            if (fullChunkSize % blockSize != 0) {
                throw IllegalArgumentException("BUG: illegal (chunk_size, block_size)=($fullChunkSize, $blockSize)")
            }
            val fileLen = File(partFile).length()
            log.warn("fcs=$fullChunkSize, file size=$fileLen")
            val partitionBlocks: Long = fileLen / blockSize
            val chunkBloks: Long = (fullChunkSize / blockSize).toLong() //typically 512
            val numChunks = Helper.Companion.round_to_multiple(partitionBlocks, chunkBloks) / chunkBloks
            log.warn("partitionBlocks=$partitionBlocks,  numChunks=$numChunks")
            for (i in 0 until numChunks) {
                val startBlock = i * chunkBloks
                val numBlocks = minOf(chunkBloks, partitionBlocks - i * chunkBloks)
                val dstExtent = UpdateMetadata.Extent.newBuilder()
                    .setStartBlock(startBlock)
                    .setNumBlocks(numBlocks)
                    .build()
                val op = UpdateMetadata.InstallOperation.newBuilder()
                    .setType(Type.REPLACE)
                    .addDstExtents(dstExtent)
                    .build()
                log.info("op<${i}> $op")
                ChunkProcessor("$partName-operation-${i}/$numChunks", op, blockSize, partFile).ProcessChunk()
            }
        }

        fun appendData() {

        }
    }

    companion object {
        val log = LoggerFactory.getLogger(DeltaGenerator::class.java.name)
    }
}