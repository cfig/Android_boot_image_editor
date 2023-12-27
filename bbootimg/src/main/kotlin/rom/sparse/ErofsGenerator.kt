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

        val newArgs =
            StringBuilder("--hash_algorithm " + ai.auxBlob!!.hashTreeDescriptors.get(0).hash_algorithm)
        ai.auxBlob!!.propertyDescriptors.forEach {
            newArgs.append(" ")
            newArgs.append("--prop ${it.key}:${it.value}")
        }
        log.info("newArgs: $newArgs")
        signingArgs = newArgs.toString()

        val mkfsBin = "aosp/plugged/bin/mkfs.erofs"
        val fc = "${workDir}file_contexts"
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
        log.info("partition_size(raw): $partition_size")
        partition_size = Helper.round_to_multiple(partition_size, 4096)
        log.info("partition_size(round 4k): $partition_size")
        val verityImageBuilder = ErofsGenerator(mount_point)

        partition_size = verityImageBuilder.calculateSizeAndReserved(partition_size)
        log.info("partition_size(calc reserve): $partition_size")
        partition_size = verityImageBuilder.calculateDynamicPartitionSize(partition_size)
        log.info("partition_size(calc dynamic): $partition_size")
        log.info("Allocating $partition_size for $partitionName")
        partition_size = verityImageBuilder.calculateMaxImageSize(partition_size)
        log.info("partition_size(calc max): $partition_size")

        val image_size = File(image_file).length()

        verityImageBuilder.addFooter(image_file)
    }

    override fun calculateSizeAndReserved(size: Long): Long {
        return maxOf((size * 1003 / 1000), (256 * 1024))
    }

    companion object {
        private val log = LoggerFactory.getLogger(ErofsGenerator::class.java)
        const val BLOCK_SIZE = 4096 // Replace this with the actual block size.
    }
}
