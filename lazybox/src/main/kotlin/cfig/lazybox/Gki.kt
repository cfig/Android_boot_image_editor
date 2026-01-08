package cfig.lazybox

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.security.MessageDigest
import kotlin.io.path.createTempDirectory
import kotlin.system.exitProcess

@JsonIgnoreProperties(ignoreUnknown = true)
data class GkiBuild(
    val name: String,
    val branches: List<Branch>
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Branch(
    val name: String,
    val kernel_version: String,
    val releases: List<Release>
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Release(
    val tag: String,
    val date: String,
    val sha1: String,
    val kernel_bid: String
)

fun printHelp() {
    println("""
        Usage:
          kotlinc -script gki.kts           # Run interactive GKI JSON downloader/parser
          kotlinc -script gki.kts <dir>     # Process GKI modules from input directory
          kotlinc -script gki.kts -h        # Show this help message
    """.trimIndent())
}

fun runGkiJsonLogic() {
    println("Running GKI JSON logic...")
    val todoFile = File("to-do.txt")

    val branchesMap = listOf(
        "android14-5_15" to "https://source.android.com/static/docs/core/architecture/kernel/gki-android14-5_15-release-builds.json",
        "android14-6_1" to "https://source.android.com/static/docs/core/architecture/kernel/gki-android14-6_1-release-builds.json",
        "android15-6_6" to "https://source.android.com/static/docs/core/architecture/kernel/gki-android15-6_6-release-builds.json",
        "android16-6_12" to "https://source.android.com/static/docs/core/architecture/kernel/gki-android16-6_12-release-builds.json"
    )

    println("Select a GKI branch:")
    branchesMap.forEachIndexed { index, (name, _) ->
        println("${index + 1}. $name")
    }

    print("Enter choice (1-${branchesMap.size}) [Default: 1]: ")
    val input = readlnOrNull()
    val choice = if (input.isNullOrBlank()) 1 else input.toIntOrNull()

    if (choice == null || choice !in 1..branchesMap.size) {
        println("Invalid choice.")
        return
    }

    val (branchName, url) = branchesMap[choice - 1]
    val jsonFileName = "gki-$branchName-release-builds.json"
    val file = File(jsonFileName)

    if (!file.exists()) {
        println("File $jsonFileName not found, downloading from $url.")
        try {
            val client = OkHttpClient()
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("Request failed: $response")
                file.writeText(response.body!!.string())
            }
            println("Download complete.")
        } catch (e: Exception) {
            println("Download failed: ${e.message}")
            return
        }
    }

    val content = file.readText()
    val jsonString: String
    val jsonStartMarker = "<code translate=\"no\" dir=\"ltr\">"
    val startIndex = content.indexOf(jsonStartMarker)

    if (startIndex != -1) {
        val fromStart = content.substring(startIndex + jsonStartMarker.length)
        jsonString = fromStart.substringBefore("</code>").trim()
    } else if (content.trim().startsWith("{")) {
        jsonString = content.trim()
    } else {
        println("Error: JSON data not found in file.")
        return
    }

    val mapper = jacksonObjectMapper()
    val gkiBuild: GkiBuild
    try {
        gkiBuild = mapper.readValue(jsonString)
    } catch (e: Exception) {
        println("JSON parsing failed: ${e.message}")
        return
    }

    val expectedNameFromFile = jsonFileName.substringAfter("gki-").substringBefore("-release-builds.json")
    if (gkiBuild.name.replace('_', '.') != expectedNameFromFile.replace('_', '.')) {
        println("Error: JSON name ('${gkiBuild.name}') does not match expected name from filename ('$expectedNameFromFile').")
        return
    }

    println("\nFound ${gkiBuild.branches.size} branches. Please select one:")
    gkiBuild.branches.forEachIndexed { index, branch ->
        println("${index + 1}. ${branch.name}")
    }

    print("Enter choice (1-${gkiBuild.branches.size}): ")
    val branchChoice = readlnOrNull()?.toIntOrNull()
    if (branchChoice == null || branchChoice !in 1..gkiBuild.branches.size) {
        println("Invalid choice.")
        return
    }

    val selectedBranch = gkiBuild.branches[branchChoice - 1]
    println("\n--- Branch Info ---\n${mapper.writerWithDefaultPrettyPrinter().writeValueAsString(selectedBranch)}")

    if (selectedBranch.releases.isEmpty()) {
        println("\nNo releases available for this branch.")
        return
    }

    println("\nFound ${selectedBranch.releases.size} releases. Please select one:")
    selectedBranch.releases.forEachIndexed { index, release ->
        println("${index + 1}. ${release.tag}")
    }

    print("Enter choice (1-${selectedBranch.releases.size}): ")
    val releaseChoice = readlnOrNull()?.toIntOrNull()
    if (releaseChoice == null || releaseChoice !in 1..selectedBranch.releases.size) {
        println("Invalid choice.")
        return
    }

    val selectedRelease = selectedBranch.releases[releaseChoice - 1]
    val tempVal = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(selectedRelease)
    println("\n--- Release Info ---\n$tempVal")

    // --- URL Generation Logic ---
    println("\nGenerating URL list...")

    val kernelBid = selectedRelease.kernel_bid

    val urlsToGenerate = listOf(
        // Debug
        "https://ci.android.com/builds/submitted/$kernelBid/kernel_debug_aarch64/latest/boot.img",
        "https://ci.android.com/builds/submitted/$kernelBid/kernel_debug_aarch64/latest/system_dlkm_staging_archive.tar.gz",
        "https://ci.android.com/builds/submitted/$kernelBid/kernel_debug_aarch64/latest/vmlinux.symvers",
        "https://ci.android.com/builds/submitted/$kernelBid/kernel_debug_aarch64/latest/view/BUILD_INFO",
        // Release
        "https://ci.android.com/builds/submitted/$kernelBid/kernel_aarch64/latest/signed/certified-boot-img-$kernelBid.tar.gz",
        "https://ci.android.com/builds/submitted/$kernelBid/kernel_aarch64/latest/system_dlkm_staging_archive.tar.gz",
        "https://ci.android.com/builds/submitted/$kernelBid/kernel_aarch64/latest/vmlinux.symvers",
        "https://ci.android.com/builds/submitted/$kernelBid/kernel_aarch64/latest/view/BUILD_INFO"
    )

    todoFile.writeText(urlsToGenerate.joinToString("\n"))
    println("URL list successfully written to ${todoFile.path}")
    println("\nScript execution complete.")
    File("bid_$kernelBid").mkdir()
}

// --- Logic from gki2.kts ---
fun runGki2Logic(inputPath: String) {
    println("Running GKI Modules Processing logic...")
    val inputDir = File(inputPath)

    if (!inputDir.exists() || !inputDir.isDirectory) {
        System.err.println("Error: Input directory '$inputPath' does not exist or is not a directory.")
        exitProcess(1)
    }

    println("Input directory: ${inputDir.absolutePath}")

    val outputDir = File("gki_modules")
    println("Cleaning up and creating output directory: ${outputDir.absolutePath}")
    if (outputDir.exists()) {
        outputDir.deleteRecursively()
    }
    outputDir.mkdir()

    // --- Boot Image Handling ---
    val certifiedBootImg = inputDir.walk().firstOrNull { it.isFile && it.name.startsWith("certified-boot-img-") && it.name.endsWith(".tar.gz") }

    if (certifiedBootImg != null) {
        println("Found certified boot image: ${certifiedBootImg.name}")
        val tempBootDir = createTempDirectory("boot_img_extraction").toFile()
        println("Extracting ${certifiedBootImg.name} to ${tempBootDir.absolutePath}...")
        "tar -xzf ${certifiedBootImg.absolutePath} -C ${tempBootDir.absolutePath}".runCommand()

        val bootImgInTar = tempBootDir.resolve("boot.img")
        if (!bootImgInTar.exists()) {
            System.err.println("Error: boot.img not found inside ${certifiedBootImg.name}")
            tempBootDir.deleteRecursively()
            exitProcess(1)
        }

        val newBootImg = outputDir.resolve("boot-5.15.img")
        println("Copying and renaming boot.img to ${newBootImg.absolutePath}")
        bootImgInTar.copyTo(newBootImg, overwrite = true)
        tempBootDir.deleteRecursively()

    } else {
        val bootImg = inputDir.resolve("boot.img")
        if (!bootImg.exists()) {
            System.err.println("Error: Neither certified-boot-img-*.tar.gz nor boot.img found in '${inputDir.absolutePath}'")
            exitProcess(1)
        }
        println("Found boot.img directly in input directory.")
        val newBootImg = outputDir.resolve("boot-5.15.img")
        println("Copying boot.img to ${newBootImg.absolutePath}")
        bootImg.copyTo(newBootImg, overwrite = true)
    }


    // --- Kernel Modules Handling ---
    val systemDlkm = inputDir.resolve("system_dlkm_staging_archive.tar.gz")

    if (!systemDlkm.exists()) {
        System.err.println("Error: system_dlkm_staging_archive.tar.gz not found in '${inputDir.absolutePath}'")
        exitProcess(1)
    }

    println("Found kernel modules archive: ${systemDlkm.name}")
    val tempModulesDir = createTempDirectory("modules_extraction").toFile()
    println("Extracting ${systemDlkm.name} to ${tempModulesDir.absolutePath}...")
    "tar -xzf ${systemDlkm.absolutePath} -C ${tempModulesDir.absolutePath}".runCommand()

    val modulesDir = tempModulesDir.resolve("flatten/lib/modules")
    if (!modulesDir.exists() || !modulesDir.isDirectory) {
        System.err.println("Error: 'flatten/lib/modules' directory not found inside the extracted archive.")
        tempModulesDir.deleteRecursively()
        exitProcess(1)
    }

    println("Copying .ko files to ${outputDir.absolutePath}...")
    modulesDir.walk().forEach {
        if (it.isFile && it.extension == "ko") {
            it.copyTo(outputDir.resolve(it.name), overwrite = true)
        }
    }
    tempModulesDir.deleteRecursively()
    println("Finished copying kernel modules.")


    // --- MD5 Sum Generation ---
    println("Generating md5sum.txt...")
    val md5sumFile = outputDir.resolve("md5sum.txt")
    val md5sums = mutableListOf<String>()
    outputDir.walk().sortedBy { it.name }.forEach {
        if (it.isFile && it.name != "md5sum.txt") {
            val md5 = it.md5()
            md5sums.add("$md5  ${it.name}")
            println("  - Calculated MD5 for ${it.name}")
        }
    }
    md5sumFile.writeText(md5sums.joinToString("\n"))
    println("md5sum.txt created successfully.")

    println("\nDone! All files are in '${outputDir.absolutePath}'.")
}

// --- Helpers ---
fun String.runCommand(workingDir: File = File(".")) {
    val process = ProcessBuilder(*split(" ").toTypedArray())
        .directory(workingDir)
        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()
    if (process.waitFor() != 0) {
        throw RuntimeException("Failed to run command: $this")
    }
}

fun File.md5(): String {
    val md = MessageDigest.getInstance("MD5")
    this.inputStream().use {
        val buffer = ByteArray(8192)
        var bytesRead = it.read(buffer)
        while (bytesRead != -1) {
            md.update(buffer, 0, bytesRead)
            bytesRead = it.read(buffer)
        }
    }
    return md.digest().joinToString("") { "%02x".format(it) }
}

class Gki {
    companion object {
        fun run(args: Array<String>) {
            // --- Main Entry Point ---
            if (args.isNotEmpty() && (args[0] == "-h" || args[0] == "--help")) {
                printHelp()
                exitProcess(0)
            }

            if (args.isEmpty()) {
                runGkiJsonLogic()
            } else if (args.size == 1) {
                runGki2Logic(args[0])
            } else {
                println("Invalid arguments.")
                printHelp()
                exitProcess(1)
            }
        }
    }
}
