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

package avb.desc

import avb.blob.Header
import cfig.helper.CryptoHelper
import cfig.helper.Helper
import cc.cfig.io.Struct
import org.slf4j.LoggerFactory
import java.io.*
import java.security.MessageDigest
import java.util.*

class HashTreeDescriptor(
    var flags: Int = 0,
    var dm_verity_version: Int = 0,
    var image_size: Long = 0,
    var tree_offset: Long = 0,
    var tree_size: Long = 0,
    var data_block_size: Int = 0,
    var hash_block_size: Int = 0,
    var fec_num_roots: Int = 0,
    var fec_offset: Long = 0,
    var fec_size: Long = 0,
    var hash_algorithm: String = "",
    var partition_name: String = "",
    var salt: ByteArray = byteArrayOf(),
    var root_digest: ByteArray = byteArrayOf()
) : Descriptor(TAG, 0, 0) {
    var flagsInterpretation: String = ""
        get() {
            var ret = ""
            if (this.flags and Header.HashTreeDescriptorFlags.AVB_HASHTREE_DESCRIPTOR_FLAGS_DO_NOT_USE_AB.inFlags == 1) {
                ret += "1:no-A/B system"
            } else {
                ret += "0:A/B system"
            }
            return ret
        }

    constructor(data: InputStream, seq: Int = 0) : this() {
        this.sequence = seq
        val info = Struct(FORMAT_STRING).unpack(data)
        this.tag = (info[0] as ULong).toLong()
        this.num_bytes_following = (info[1] as ULong).toLong()
        this.dm_verity_version = (info[2] as UInt).toInt()
        this.image_size = (info[3] as ULong).toLong()
        this.tree_offset = (info[4] as ULong).toLong()
        this.tree_size = (info[5] as ULong).toLong()
        this.data_block_size = (info[6] as UInt).toInt()
        this.hash_block_size = (info[7] as UInt).toInt()
        this.fec_num_roots = (info[8] as UInt).toInt()
        this.fec_offset = (info[9] as ULong).toLong()
        this.fec_size = (info[10] as ULong).toLong()
        this.hash_algorithm = info[11] as String
        val partition_name_len = info[12] as UInt
        val salt_len = info[13] as UInt
        val root_digest_len = info[14] as UInt
        this.flags = (info[15] as UInt).toInt()
        val expectedSize =
            Helper.round_to_multiple(SIZE.toUInt() - 16U + partition_name_len + salt_len + root_digest_len, 8U)
        if (this.tag != TAG || this.num_bytes_following != expectedSize.toLong()) {
            throw IllegalArgumentException("Given data does not look like a hashtree descriptor")
        }

        val info2 = Struct("${partition_name_len}s${salt_len}b${root_digest_len}b").unpack(data)
        this.partition_name = info2[0] as String
        this.salt = info2[1] as ByteArray
        this.root_digest = info2[2] as ByteArray
    }

    override fun encode(): ByteArray {
        this.num_bytes_following = SIZE + this.partition_name.length + this.salt.size + this.root_digest.size - 16
        val nbf_with_padding = Helper.round_to_multiple(this.num_bytes_following.toLong(), 8)
        val padding_size = nbf_with_padding - this.num_bytes_following.toLong()
        val desc = Struct(FORMAT_STRING).pack(
            TAG,
            nbf_with_padding.toULong(),
            this.dm_verity_version,
            this.image_size,
            this.tree_offset,
            this.tree_size,
            this.data_block_size,
            this.hash_block_size,
            this.fec_num_roots,
            this.fec_offset,
            this.fec_size,
            this.hash_algorithm,
            this.partition_name.length,
            this.salt.size,
            this.root_digest.size,
            this.flags,
            null
        )
        val padding = Struct("${padding_size}x").pack(null)
        return Helper.join(desc, this.partition_name.toByteArray(), this.salt, this.root_digest, padding)
    }

    fun verify(fileNames: List<String>, parent: String = ""): Array<Any> {
        for (item in fileNames) {
            if (File(item).exists()) {
                val trimmedHash = this.genMerkleTree(item, "hash.tree")
                val readTree = ByteArray(this.tree_size.toInt())
                FileInputStream(item).use { fis ->
                    fis.skip(this.tree_offset)
                    fis.read(readTree)
                }
                val ourHtHash = CryptoHelper.Hasher.sha256(File("hash.tree").readBytes())
                val diskHtHash = CryptoHelper.Hasher.sha256(readTree)
                if (!ourHtHash.contentEquals(diskHtHash)) {
                    return arrayOf(false, "MerkleTree corrupted")
                } else {
                    log.info("VERIFY($parent): MerkleTree integrity check... PASS")
                }
                if (!this.root_digest.contentEquals(trimmedHash)) {
                    return arrayOf(false, "MerkleTree root hash mismatch")
                } else {
                    log.info("VERIFY($parent): MerkleTree root hash check... PASS")
                }
                return arrayOf(true, "")
            }
        }
        return arrayOf(false, "file not found")
    }

    private fun calcSingleHashSize(padded: Boolean = false): Int {
        val digSize = MessageDigest.getInstance(CryptoHelper.Hasher.pyAlg2java(this.hash_algorithm)).digest().size
        val padSize = Helper.round_to_pow2(digSize.toLong()) - digSize
        return (digSize + (if (padded) padSize else 0)).toInt()
    }

    private fun calcStreamHashSize(inStreamSize: Long, inBlockSize: Int): Long {
        val blockCount = (inStreamSize + inBlockSize - 1) / inBlockSize
        return Helper.round_to_multiple(blockCount * calcSingleHashSize(true), inBlockSize.toLong())
    }

    fun hashStream(
        inputStream: InputStream,
        streamSz: Long,
        blockSz: Int
    ): ByteArray {
        val hashSize = calcStreamHashSize(streamSz, blockSz)
        val bos = ByteArrayOutputStream(hashSize.toInt())
        run hashing@{
            val padSz = calcSingleHashSize(true) - calcSingleHashSize(false)
            val padding = Struct("${padSz}x").pack(0)
            var totalRead = 0L
            while (true) {
                val data = ByteArray(blockSz)
                MessageDigest.getInstance(CryptoHelper.Hasher.pyAlg2java(this.hash_algorithm)).let {
                    val bytesRead = inputStream.read(data)
                    if (bytesRead <= 0) {
                        return@hashing
                    }
                    totalRead += bytesRead
                    if (totalRead > streamSz) {
                        return@hashing
                    }
                    it.update(this.salt)
                    it.update(data)
                    val dg = it.digest()
                    bos.write(dg)
                    bos.write(padding)
                    //log.info(Helper.toHexString(dg))
                }
            }
        }//hashing

        if (hashSize > bos.size()) {
            bos.write(Struct("${hashSize - bos.size()}x").pack(0))
        }
        return bos.toByteArray()
    }

    fun genMerkleTree(fileName: String, treeFile: String? = null): ByteArray {
        log.info("generate Merkle tree()")
        val plannedTree = calcMerkleTree(this.image_size, this.hash_block_size, calcSingleHashSize(true))
        val calcRootHash: ByteArray
        treeFile?.let { File(treeFile).let { if (it.exists()) it.delete() }}
        val raf = if (treeFile.isNullOrBlank()) null else RandomAccessFile(treeFile, "rw")
        val l0: ByteArray
        log.info("Hashing Level #${plannedTree.size}..." + plannedTree.get(plannedTree.size - 1))
        FileInputStream(fileName).use { fis ->
            l0 = hashStream(
                fis, this.image_size,
                this.data_block_size
            )
        }
        if (DEBUG) FileOutputStream("hash.file" + plannedTree.size).use { it.write(l0) }
        raf?.seek(plannedTree.get(plannedTree.size - 1).hashOffset)
        raf?.write(l0)
        var dataToHash: ByteArray = l0
        var i = plannedTree.size - 1
        while (true) {
            val levelHash = hashStream(dataToHash.inputStream(), dataToHash.size.toLong(), this.hash_block_size)
            if (DEBUG) FileOutputStream("hash.file$i").use { it.write(levelHash) }
            if (dataToHash.size <= this.hash_block_size) {
                log.debug("Got root hash: " + Helper.toHexString(levelHash))
                calcRootHash = levelHash
                break
            }
            log.info("Hashing Level #$i..." + plannedTree.get(i - 1))
            raf?.seek(plannedTree.get(i - 1).hashOffset)
            raf?.write(levelHash)
            dataToHash = levelHash
            i--
        }
        raf?.close()
        raf?.let { log.info("MerkleTree(${this.partition_name}) saved to $treeFile") }
        return calcRootHash.sliceArray(0 until calcSingleHashSize(false))
    }

    override fun toString(): String {
        return "HashTreeDescriptor(dm_verity_version=$dm_verity_version, image_size=$image_size, " +
                "tree_offset=$tree_offset, tree_size=$tree_size, data_block_size=$data_block_size, " +
                "hash_block_size=$hash_block_size, fec_num_roots=$fec_num_roots, fec_offset=$fec_offset, " +
                "fec_size=$fec_size, hash_algorithm='$hash_algorithm', partition_name='$partition_name', " +
                "salt=${salt.contentToString()}, root_digest=${Arrays.toString(root_digest)}, flags=$flags)"
    }

    companion object {
        const val TAG: Long = 1L
        private const val RESERVED = 60L
        private const val SIZE = 120 + RESERVED
        private const val FORMAT_STRING = "!2QL3Q3L2Q32s4L${RESERVED}x"
        private val log = LoggerFactory.getLogger(HashTreeDescriptor::class.java)
        private const val DEBUG = false

        class MerkleTree(
            var dataSize: Long = 0,
            var dataBlockCount: Long = 0,
            var hashSize: Long = 0,
            var hashOffset: Long = 0
        ) {
            override fun toString(): String {
                return String.format(Locale.getDefault(),
                    "MT{data: %10s(%6s blocks), hash: %7s @%-5s}",
                    dataSize,
                    dataBlockCount,
                    hashSize,
                    hashOffset
                )
            }
        }

        fun calcMerkleTree(fileSize: Long, blockSize: Int, digestSize: Int): List<MerkleTree> {
            var levelDataSize: Long = fileSize
            var levelNo = 0
            val tree: MutableList<MerkleTree> = mutableListOf()
            while (true) {
                //raw data in page of blockSize
                val blockCount = (levelDataSize + blockSize - 1) / blockSize
                if (1L == blockCount) {
                    break
                }
                //digest size in page of blockSize
                val hashSize = Helper.round_to_multiple(blockCount * digestSize, blockSize.toLong())
                tree.add(0, MerkleTree(levelDataSize, blockCount, hashSize))
                levelDataSize = hashSize
                levelNo++
            }
            for (i in 1 until tree.size) {
                tree[i].hashOffset = tree[i - 1].hashOffset + tree[i - 1].hashSize
            }
            tree.forEachIndexed { index, merkleTree ->
                log.info("Level #${index + 1}: $merkleTree")
            }
            val treeSize = tree.sumOf { it.hashSize }
            log.info("tree size: $treeSize(" + Helper.humanReadableByteCountBin(treeSize) + ")")
            return tree
        }
    }
}
