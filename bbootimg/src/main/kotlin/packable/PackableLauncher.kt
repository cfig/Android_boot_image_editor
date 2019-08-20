package cfig.packable

import cfig.UnifiedConfig
import cfig.sparse_util.SparseImg
import org.slf4j.LoggerFactory
import java.io.File
import java.util.regex.Pattern
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance

class PackableLauncher

@ExperimentalUnsignedTypes
fun main(args: Array<String>) {
    val log = LoggerFactory.getLogger(PackableLauncher::class.java)
    val packablePool = mutableMapOf<List<String>, KClass<IPackable>>()
    listOf(DtboParser(), VBMetaParser(), BootImgParser(), SparseImg()).forEach {
        @Suppress("UNCHECKED_CAST")
        packablePool.put(it.capabilities(), it::class as KClass<IPackable>)
    }
    packablePool.forEach {
        log.debug("" + it.key + "/" + it.value)
    }
    var targetFile: String? = null
    var targetHandler: KClass<IPackable>? = null
    run found@{
        File(".").listFiles().forEach { file ->
            packablePool.forEach { p ->
                for (item in p.key) {
                    if (Pattern.compile(item).matcher(file.name).matches()) {
                        log.debug("Found: " + file.name + ", " + item)
                        targetFile = file.name
                        targetHandler = p.value
                        return@found
                    }
                }
            }
        }
    }

    if (targetHandler != null) {
        log.warn("Active image target: $targetFile")
        when (args[0]) {
            "unpack" -> {
                if (File(UnifiedConfig.workDir).exists()) File(UnifiedConfig.workDir).deleteRecursively()
                File(UnifiedConfig.workDir).mkdirs()
                targetHandler!!.createInstance().unpack(targetFile!!)
            }
            "pack" -> {
                targetHandler!!.createInstance().pack(targetFile!!)
            }
            "flash" -> {
                targetHandler!!.createInstance().flash(targetFile!!)
            }
            else -> {
                log.error("Unknown cmd: " + args[0])
            }
        }
    } else {
        log.warn("Nothing to do")
    }
}
