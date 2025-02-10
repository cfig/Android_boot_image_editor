package cfig.lazybox

import cfig.helper.Helper
import cfig.helper.Helper.Companion.check_call
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

class ImageRelease {
    companion object {
        private val log = LoggerFactory.getLogger(ImageRelease::class.java)
        fun run() {
            val buildId = Helper.adbCmd("getprop ro.build.id").lowercase()
            val variant = Helper.adbCmd("getprop ro.build.type")
            val product = Helper.adbCmd("getprop ro.build.product")
            val rel = Helper.adbCmd("getprop ro.build.version.release")
            val increment = Helper.adbCmd("getprop ro.build.version.incremental")
            val fp = Helper.adbCmd("getprop ro.build.fingerprint")

            val computFacDir = "$product-factory-$buildId"
            val computFacZip = "$product-factory-$buildId-$increment.zip"
            val computOtaZip = "$product-ota-$buildId-$increment.zip"
            val computEmmcZip = "$product-eMMCimg-$buildId-$increment.zip"

            log.info("fingerprint: $fp")
            log.info("$product-factory-$buildId-$increment ->  $product-factory-$buildId")
            log.info("$product-ota-$buildId-$increment")
            log.info("$product-eMMCimg-$buildId-$increment")

            //factory
            if (File("factory.zip").exists()) {
                "rm -fr factory_image factory_img $computFacDir $computFacZip".check_call()
                "unzip factory.zip".check_call()
                val facDir = if (File("factory_img").exists()) {
                    //user
                   "factory_img"
                } else if (File("factory_image").exists()) {
                    //userdebug
                    "factory_image"
                } else {
                    throw IllegalStateException("can not find factory image folder")
                }
                File(facDir).listFiles()?.filter { it.name.endsWith(".sh") }?.forEach { it.delete() }
                "cp -v /tftp/flash_platypus.sh $facDir/flash.sh".check_call()
                "mv -v $facDir $computFacDir".check_call()
                "zip $computFacZip -r $computFacDir".check_call()
                "rm -fr $computFacDir".check_call()
            }

            File("factory.zip").delete()
            if (File("ota.zip").exists()) {
                Files.move(Paths.get("ota.zip"), Paths.get(computOtaZip))
            }
            if (File("emmc.zip").exists()) {
                Files.move(Paths.get("emmc.zip"), Paths.get(computEmmcZip))
            }

            log.info("fingerprint: $fp")
            log.info("$product-factory-$buildId-$increment ->  $product-factory-$buildId")
            log.info(computFacZip)
            log.info(computOtaZip)
            log.info(computEmmcZip)
        }
    }

}