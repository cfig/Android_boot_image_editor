package cfig.lazybox

import cfig.helper.Helper
import java.io.File

class CompileCommand {
    fun run(workDir: String, stem: String) {
        val simg2simg = "simg2simg"
        val cmd = "$simg2simg $workDir/$stem.subimg $workDir/$stem.subimg 524288000"
        Helper.powerRun(cmd, null)
        val emmcImageListFile = File(workDir, "emmc_image_list")
        val theLines = emmcImageListFile.readLines().toMutableList()
        val stemLine = (theLines.filter { it.startsWith("$stem.subimg,") }).get(0).split(",")
        check(stemLine.size == 2)
        val stemPart = stemLine[1]
        theLines.apply {
            removeIf { it.startsWith("$stem.subimg") }
            val superImageFiles = File(workDir).listFiles { file -> file.name.startsWith("$stem.subimg.") }
            superImageFiles.forEach {
                val newLine = "${it.name},$stemPart"
                println("Adding $newLine")
                add(newLine)
            }
        }
        emmcImageListFile.writeText(theLines.joinToString("\n") + "\n")
    }
}
