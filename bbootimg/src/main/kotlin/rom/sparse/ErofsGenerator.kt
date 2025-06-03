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
import org.apache.commons.exec.environment.EnvironmentUtils
import org.slf4j.LoggerFactory
import java.io.File

class ErofsGenerator(inPartitionName: String) : BaseGenerator(inPartitionName, AVB_HASHTREE_FOOTER) {
    fun pack(
        ai: AVBInfo,
        mount_point: String,
        productOut: String = "dlkm/system",
        srcDir: String,
        image_file: String
    ) {
        log.warn("pack: $mount_point, $productOut, $srcDir")

        // update signing args
        val newArgs =
            StringBuilder("--hash_algorithm " + ai.auxBlob!!.hashTreeDescriptors.get(0).hash_algorithm)
        ai.auxBlob!!.propertyDescriptors.forEach {
            newArgs.append(" ")
            newArgs.append("--prop ${it.key}:${it.value}")
        }
        salt = Helper.toHexString(ai.auxBlob!!.hashTreeDescriptors.get(0)!!.salt)
        log.info("newArgs: $newArgs")
        signingArgs = newArgs.toString()

        val mkfsBin = "aosp/plugged/bin/mkfs.erofs"
        val fc = File(workDir, "file_contexts").path
        val cmd = CommandLine.parse(mkfsBin).apply {
            addArguments("-z lz4hc,9")
            addArguments("--mount-point $mount_point")
            addArguments("--product-out $productOut")
            addArguments("--file-contexts $fc")
            addArgument(image_file)
            addArgument(srcDir)
        }
        val env = EnvironmentUtils.getProcEnvironment().apply {
            put("PATH", "aosp/plugged/bin:" + System.getenv("PATH"))
            put("LD_LIBRARY_PATH", "aosp/plugged/lib:" + System.getenv("LD_LIBRARY_PATH"))
        }

        DefaultExecutor().execute(cmd, env)

        val ret2 = Helper.powerRun("du -b -k -s $image_file", null)
        var partition_size = String(ret2.get(0)).split("\\s".toRegex()).get(0).toLong() * 1024
        log.info("[calc 1/5] partition_size(raw): $partition_size")
        partition_size = calculateSizeAndReserved(partition_size)
        log.info("[calc 2/5] partition_size(calc reserve): $partition_size")
        partition_size = Helper.round_to_multiple(partition_size, 4096)
        log.info("[calc 3/5] partition_size(round 4k): $partition_size")

        partition_size = super.calculateDynamicPartitionSize(partition_size)
        log.info("[calc 4/5] partition_size(calc dynamic): $partition_size")
        log.info("Allocating $partition_size for $partitionName")
        val partitionSize = partition_size
        partition_size = super.calculateMaxImageSize(partition_size)
        log.info("[calc 5/5] partition_size(calc max): $partition_size")

        val imageSize = File(image_file).length()
        log.info("info_dict: imageSize = $imageSize")
        log.info("info_dict: partitionSize = $partitionSize")
        super.addFooter(image_file)
    }

    override fun calculateSizeAndReserved(size: Long): Long {
        return maxOf((size * 1003 / 1000), (256 * 1024))
    }

    companion object {
        private val log = LoggerFactory.getLogger(ErofsGenerator::class.java)
        const val BLOCK_SIZE = 4096 // Replace this with the actual block size.
    }
}
