// Copyright 2023-2024 yuyezhong@gmail.com
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

package rom.sparse

import avb.AVBInfo
import cfig.helper.Helper
import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.apache.commons.exec.PumpStreamHandler
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Properties
import kotlin.math.ceil

class Ext4Generator(inPartitionName: String = "NA") : BaseGenerator(partitionName = inPartitionName, footerType = AVB_HASHTREE_FOOTER) {
    var theInodeCount = 0L
    var theSize = 0L
    fun pack(
        ai: AVBInfo,
        mount_point: String, productOut: String = "dlkm/system", srcDir: String, outFile: String
    ) {
        log.warn("pack: $mount_point, $productOut, $srcDir")
        val newArgs = StringBuilder("--hash_algorithm " + ai.auxBlob!!.hashTreeDescriptors.get(0).hash_algorithm)
        ai.auxBlob!!.propertyDescriptors.forEach {
            newArgs.append(" ")
            newArgs.append("--prop ${it.key}:${it.value}")
        }
        log.info("newArgs: $newArgs")
        signingArgs = newArgs.toString()

        //XXXX: calc src dir
        val ret2 = Helper.powerRun("du -b -k -s $srcDir", null)
        theSize = String(ret2.get(0)).split("\\s".toRegex()).get(0).toLong() * 1024
        log.info("theSize(raw): $theSize")
        val verityImageBuilder = Ext4Generator(mount_point)
        theSize = verityImageBuilder.calculateSizeAndReserved(theSize)
        log.info("theSize(calc reserve): $theSize")
        theSize = Helper.round_to_multiple(theSize, 4096)
        log.info("theSize(round 4k): $theSize")

        theInodeCount = getInodeUsage(srcDir)
        log.info("extfs_inode_count: $theInodeCount")

        //build fs
        verityImageBuilder.makeFileSystem(outFile, theSize, theInodeCount, mount_point, partitionName)
        //addFooter(image_file)
        val fs_dict = getFilesystemCharacteristics(outFile)
        log.warn("removing intermediate $outFile")
        File(outFile).delete()
        log.info("XX: free blocks=" + fs_dict.getProperty("Free blocks"))
        log.info("XX: free_size = " + fs_dict.getProperty("Free blocks").toLong() * BLOCK_SZ)
        theSize -= fs_dict.getProperty("Free blocks", "0").toLong() * BLOCK_SZ
        val reservedSize = 0
        theSize += reservedSize

        if (reservedSize == 0) {
            // add 0.3% margin
            theSize = theSize * 1003 / 1000
        }

        // Use a minimum size, otherwise, we will fail to calculate an AVB footer
        // or fail to construct an ext4 image.
        theSize = maxOf(theSize, 256 * 1024)
        val blockSize = fs_dict.getProperty("Block size", "4096").toLong()
        if (blockSize <= 4096) {
            theSize = Helper.round_to_multiple(theSize, 4096)
        } else {
            theSize = ((theSize + blockSize - 1) / blockSize) * blockSize
        }

        var inodes = fs_dict.getProperty("Inode count", "-1").toLong()
        if (inodes == -1L) {
            inodes = theInodeCount
        }
        inodes -= fs_dict.getProperty("Free inodes", "0").toLong()

        // add 0.2% margin or 1 inode, whichever is greater
        val spareInodes = inodes * 2 / 1000
        val minSpareInodes = 1
        if (spareInodes < minSpareInodes) {
            inodes += minSpareInodes
        } else {
            inodes += spareInodes
        }

        theInodeCount = inodes
        log.info("Allocating $inodes Inodes for $outFile.")
        log.info("theSize = $theSize")

        theSize = verityImageBuilder.calculateDynamicPartitionSize(theSize)
        log.info("theSize(calc dynamic): $theSize")
        log.info("Allocating $theSize for $partitionName")
        theSize = verityImageBuilder.calculateMaxImageSize(theSize)
        log.info("theSize(calc max): $theSize")

        //build fs again
        verityImageBuilder.makeFileSystem(outFile, theSize, theInodeCount, mount_point, partitionName)

        val image_size = File(outFile).length()
        log.info("image size: $image_size")
        addFooter(outFile)
    }

    fun makeFileSystem(outFile: String, fs_size: Long, inodes: Long, mount_point: String, label: String) {
        DefaultExecutor().apply {
            streamHandler = PumpStreamHandler(System.out, System.err)
        }.execute(CommandLine.parse("mke2fs").apply {
            addArguments("-O ^has_journal")
            addArguments("-L $label")
            addArguments("-N $inodes")
            addArguments("-I 256")
            addArguments("-M $mount_point")
            addArguments("-m 0")
            addArguments("-t ext4")
            addArguments("-b $BLOCK_SZ")
            addArgument(outFile)
            addArgument((fs_size / BLOCK_SZ).toString())
        }.also { log.warn(it.toString()) })

        DefaultExecutor().apply {
            streamHandler = PumpStreamHandler(System.out, System.err)
        }.execute(CommandLine.parse("aosp/plugged/bin/e2fsdroid").apply {
            addArguments("-e")
            addArguments("-p out/target/product/shiba/system")
            addArgument("-s")
            addArguments("-S " + File(workDir, "file_contexts.bin").path)
            addArguments("-f " + Helper.prop("workDir") + "/$mount_point")
            addArguments("-a /$mount_point")
            addArgument(outFile)
        }.also { log.warn(it.toString()) })
    }

    fun getInodeUsage(path: String): Long {
        // Increase by > 6% as the number of files and directories is not the whole picture.
        val inodes = Files.walk(Paths.get(path)).count()
        val spareInodes = ceil(inodes * 0.06).toInt()
        val minSpareInodes = 12
        return inodes + maxOf(spareInodes, minSpareInodes)
    }

    override fun calculateSizeAndReserved(size: Long): Long {
        return 16 * 1024 * 1024 + size
    }

    private fun getFilesystemCharacteristics(file: String): Properties {
        val fsDict = Helper.powerRun("tune2fs -l $file", null).get(0)
        return Properties().apply {
            String(fsDict).split("\n".toRegex()).forEach {
                //get the key and value separately in "key : value" format from line
                val keyValue = it.split(":".toRegex(), 2)
                if (keyValue.size == 2) {
                    val key = keyValue[0].trim { it <= ' ' }
                    val value = keyValue[1].trim { it <= ' ' }
                    log.debug("X: $key=$value")
                    setProperty(key, value)
                }
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(Ext4Generator::class.java)
        private val BLOCK_SZ = 4096
    }
}
