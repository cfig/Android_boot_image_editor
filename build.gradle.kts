import java.util.regex.Matcher
import java.util.regex.Pattern
import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.apache.commons.exec.PumpStreamHandler

val GROUP_ANDROID = "android"
if (parseGradleVersion(gradle.gradleVersion) < 5) {
    logger.error("ERROR: Gradle Version MUST >= 5.0, current is {}", gradle.gradleVersion)
    throw RuntimeException("ERROR: Gradle Version")
} else {
    logger.info("Gradle Version {}", gradle.gradleVersion)
}

buildscript {
    repositories {
        jcenter()
    }
    dependencies {
        classpath("org.apache.commons:commons-exec:1.3")
    }
}

tasks {
    val unpackTask by register<JavaExec>("unpack") {
        group = GROUP_ANDROID
        main = "cfig.packable.PackableLauncherKt"
        classpath = files("bbootimg/build/libs/bbootimg.jar")
        this.maxHeapSize = "512m"
        args("unpack")
    }
    unpackTask.dependsOn("bbootimg:jar")

    val packTask by register<JavaExec>("pack") {
        group = GROUP_ANDROID
        main = "cfig.packable.PackableLauncherKt"
        classpath = files("bbootimg/build/libs/bbootimg.jar")
        this.maxHeapSize = "512m"
        args("pack")
    }
    packTask.dependsOn("bbootimg:jar", "aosp:boot_signer:build")

    val flashTask by register("flash", JavaExec::class) {
        group = GROUP_ANDROID
        main = "cfig.packable.PackableLauncherKt"
        classpath = files("bbootimg/build/libs/bbootimg.jar")
        this.maxHeapSize = "512m"
        args("flash")
    }
    flashTask.dependsOn("bbootimg:jar")

    //sparse image dependencies
    if (System.getProperty("os.name").contains("Mac")) {
        unpackTask.dependsOn("aosp:libsparse:simg2img:installReleaseMacos")
        packTask.dependsOn("aosp:libsparse:img2simg:installReleaseMacos")
        packTask.dependsOn("aosp:mkbootfs:installReleaseMacos")
    } else {
        unpackTask.dependsOn("aosp:libsparse:simg2img:installReleaseLinux")
        packTask.dependsOn("aosp:libsparse:img2simg:installReleaseLinux")
        packTask.dependsOn("aosp:mkbootfs:installReleaseLinux")
    }
}

fun parseGradleVersion(version: String): Int {
    val VERSION_PATTERN = Pattern.compile("((\\d+)(\\.\\d+)+)(-(\\p{Alpha}+)-(\\w+))?(-(SNAPSHOT|\\d{14}([-+]\\d{4})?))?")
    val matcher = VERSION_PATTERN.matcher(version)
    if (!matcher.matches()) {
        throw IllegalArgumentException(String.format("'%s' is not a valid Gradle version string (examples: '1.0', '1.0-rc-1')", version))
    }
    val versionPart: String = matcher.group(1)
    val majorPart = Integer.parseInt(matcher.group(2), 10)
    logger.info("Gradle: versionPart {}, majorPart {}", versionPart, majorPart)
    return majorPart
}
