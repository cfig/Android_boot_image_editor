package cfig

@ExperimentalUnsignedTypes
data class ParamConfig(
        //file input
        var kernel: String = UnifiedConfig.workDir + "kernel",
        var ramdisk: String? = UnifiedConfig.workDir + "ramdisk.img.gz",
        var second: String? = UnifiedConfig.workDir + "second",
        var dtbo: String? = UnifiedConfig.workDir + "recoveryDtbo",
        var dtb: String? = UnifiedConfig.workDir + "dtb",
        var cfg: String = UnifiedConfig.workDir + "bootimg.json",
        val mkbootimg: String = "./external/mkbootimg")
