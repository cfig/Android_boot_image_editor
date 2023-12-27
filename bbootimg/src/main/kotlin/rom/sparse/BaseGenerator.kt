package rom.sparse

import cfig.helper.Helper
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.InputStreamReader

open class BaseGenerator(
    val partitionName: String = "NA",
    val footerType: String = AVB_HASHTREE_FOOTER
) {
    var partitionSize: Long = 0
    var imageSize: Long = 0
    var keyPath: String = ""
    var algorithm: String = ""
    var salt: String = ""
    val avbtool = String.format(Helper.prop("avbtool"), "v1.2")
    var signingArgs = "--hash_algorithm sha256 " +
            "--prop com.android.build.the_partition_name.os_version:14 " +
            "--prop com.android.build.the_partition_name.fingerprint:anonymous/device/device:14/UD1A.230803.041/buildid:userdebug/test-keys"
    val workDir = Helper.prop("workDir")

    fun calculateMinPartitionSize(imageSize: Long, sizeCalculator: ((Long) -> Long)? = null): Long {
        var sizeCalculatorCopy = sizeCalculator
        if (sizeCalculatorCopy == null) {
            sizeCalculatorCopy = this::calculateMaxImageSize
        }

        // Use image size as partition size to approximate final partition size.
        val calculated = sizeCalculatorCopy(imageSize)
        var imageRatio = calculated / imageSize.toDouble()
        log.info("image_ratio = $imageRatio, image_size = $imageSize, calc = $calculated")

        // Prepare a binary search for the optimal partition size.
        var lo = (imageSize / imageRatio).toLong() / ErofsGenerator.BLOCK_SIZE * ErofsGenerator.BLOCK_SIZE - ErofsGenerator.BLOCK_SIZE

        // Ensure lo is small enough: max_image_size should <= image_size.
        var delta = ErofsGenerator.BLOCK_SIZE
        var maxImageSize = sizeCalculatorCopy(lo)
        while (maxImageSize > imageSize) {
            imageRatio = maxImageSize / lo.toDouble()
            lo = (imageSize / imageRatio).toLong() / ErofsGenerator.BLOCK_SIZE * ErofsGenerator.BLOCK_SIZE - delta
            delta *= 2
            maxImageSize = sizeCalculatorCopy(lo)
        }

        var hi = lo + ErofsGenerator.BLOCK_SIZE

        // Ensure hi is large enough: max_image_size should >= image_size.
        delta = ErofsGenerator.BLOCK_SIZE
        maxImageSize = sizeCalculatorCopy(hi)
        while (maxImageSize < imageSize) {
            imageRatio = maxImageSize / hi.toDouble()
            hi = (imageSize / imageRatio).toLong() / ErofsGenerator.BLOCK_SIZE * ErofsGenerator.BLOCK_SIZE + delta
            delta *= 2
            maxImageSize = sizeCalculatorCopy(hi)
        }

        var partitionSize = hi

        // Start the binary search.
        while (lo < hi) {
            val mid = ((lo + hi) / (2 * ErofsGenerator.BLOCK_SIZE)) * ErofsGenerator.BLOCK_SIZE
            maxImageSize = sizeCalculatorCopy(mid)
            if (maxImageSize >= imageSize) {
                if (mid < partitionSize) {
                    partitionSize = mid
                }
                hi = mid
            } else {
                lo = mid + ErofsGenerator.BLOCK_SIZE
            }
        }

        log.info("CalculateMinPartitionSize($imageSize): partition_size $partitionSize.")

        return partitionSize
    }

    fun calculateMaxImageSize(partitionSize: Long? = null): Long {
        val actualPartitionSize = partitionSize ?: this.partitionSize
        require(actualPartitionSize > 0) { "Invalid partition size: $actualPartitionSize" }

        val addFooter = if (footerType == AVB_HASH_FOOTER) "add_hash_footer" else "add_hashtree_footer"
        val cmd = mutableListOf(
            avbtool,
            addFooter,
            "--partition_size",
            actualPartitionSize.toString(),
            "--calc_max_image_size"
        )
        cmd.addAll(signingArgs.split(" "))

        val processBuilder = ProcessBuilder(cmd)
        log.info(cmd.joinToString(" "))
        setProcessEnvironment(processBuilder)
        processBuilder.redirectErrorStream(true)
        val proc = processBuilder.start()

        val reader = BufferedReader(InputStreamReader(proc.inputStream))
        val output = reader.readText()

        val exitCode = proc.waitFor()
        if (exitCode != 0) {
            throw BuildVerityImageError("Failed to calculate max image size:\n$output")
        }

        val imageSize = output.trim().toLong()
        require(imageSize > 0) { "Invalid max image size: $imageSize" }

        this.imageSize = imageSize
        return imageSize
    }

    fun calculateDynamicPartitionSize(imageSize: Long): Long {
        log.info("verity_utils: CalculateDynamicPartitionSize")
        partitionSize = calculateMinPartitionSize(imageSize)
        return partitionSize
    }

    protected fun setProcessEnvironment(processBuilder: ProcessBuilder) {
        processBuilder.environment().apply {
            put("PATH", "aosp/plugged/bin:" + System.getenv("PATH"))
            put("LD_LIBRARY_PATH", "aosp/plugged/lib:" + System.getenv("LD_LIBRARY_PATH"))
        }
    }
    protected open fun calculateSizeAndReserved(size: Long): Long {
        throw IllegalAccessException("not implemented")
    }
    fun addFooter(outFile: String) {
        val addFooter = if (footerType == AVB_HASH_FOOTER) "add_hash_footer" else "add_hashtree_footer"
        val cmd = mutableListOf(
            avbtool, addFooter,
            "--partition_size", partitionSize.toString(),
            "--partition_name", partitionName,
            "--image", outFile
        )

        if (keyPath.isNotEmpty() && algorithm.isNotEmpty()) {
            cmd.addAll(listOf("--key", keyPath, "--algorithm", algorithm))
        }

        if (salt.isNotEmpty()) {
            cmd.addAll(listOf("--salt", salt))
        }

        cmd.addAll(signingArgs.split(" "))
        val processBuilder = ProcessBuilder(cmd)
        log.info(cmd.joinToString(" "))
        setProcessEnvironment(processBuilder)
        processBuilder.redirectErrorStream(true)
        val proc = processBuilder.start()

        val reader = BufferedReader(InputStreamReader(proc.inputStream))
        val output = reader.readText()

        val exitCode = proc.waitFor()
        if (exitCode != 0) {
            throw BuildVerityImageError("Failed to add AVB footer: $output")
        }
    }

    class BuildVerityImageError(message: String) : Exception(message)
    companion object {
        private val log = LoggerFactory.getLogger(BaseGenerator::class.java)
        const val AVB_HASH_FOOTER = "avb_hash_footer"
        const val AVB_HASHTREE_FOOTER = "avb_hashtree_footer"
    }
}