import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

if (null == System.getenv("ANDROID_PRODUCT_OUT")) {
    println("ANDROID_PRODUCT_OUT not set, did U envsetup/lunch product?")
    System.exit(1)
}

val xbins = listOf("su")
val bins = listOf("sh", "logcat", "logcatd", "logd", "linker", "toolbox", "toybox", "applypatch", "debuggerd",  "reboot")
val libs = listOf("libnetutils.so", "libdl.so", "libutils.so", "libc++.so", "libc.so", "libm.so", "libz.so", "libstdc++.so", "libcutils.so", "libselinux.so", "liblog.so", "libpcrecpp.so", "libpcre2.so", "libsysutils.so", "libnl.so", "libbase.so", "libbacktrace.so", "libunwind.so", "libcrypto.so", "libpackagelistparser.so", "libpcrecpp.so", "liblzma.so", "liblogcat.so")
val initrcFiles = listOf("logcatd.rc", "logd.rc")
val toolboxLinks = listOf("df", "getevent", "iftop", "ioctl", "ionice", "log", "lsof", "nandread", "newfs_msdos", "ps", "prlimit", "renice", "sendevent", "start", "stop", "top", "uptime", "watchprops", "dd", "du")
val toyboxLinks = listOf("ps", "ls", "acpi", "basename", "blockdev", "bzcat", "cal", "cat", "chcon", "chgrp", "chmod", "chown", "chroot", "cksum", "clear", "comm", "cmp", "cp", "cpio", "cut", "date", "dirname", "dmesg", "dos2unix", "echo", "env", "expand", "expr", "fallocate", "false", "find", "free", "getenforce", "getprop", "groups", "head", "hostname", "hwclock", "id", "ifconfig", "inotifyd", "insmod", "kill", "load_policy", "ln", "logname", "losetup", "lsmod", "lsusb", "md5sum", "mkdir", "mknod", "mkswap", "mktemp", "modinfo", "more", "mount", "mountpoint", "mv", "netstat", "nice", "nl", "nohup", "od", "paste", "patch", "grep", "pidof", "pkill", "pmap", "printenv", "printf", "pwd", "readlink", "realpath", "restorecon", "rm", "rmdir", "rmmod", "route", "runcon", "sed", "seq", "setenforce", "setprop", "setsid", "sha1sum", "sleep", "sort", "split", "stat", "strings", "swapoff", "swapon", "sync", "sysctl", "tac", "tail", "tar", "taskset", "tee", "time", "timeout", "touch", "tr", "true", "truncate", "umount", "uname", "uniq", "unix2dos", "usleep", "vmstat", "wc", "which", "whoami", "xargs", "yes")

val workdir: String = "build/unzip_boot"
File("$workdir/root/system/bin").mkdirs()
File("$workdir/root/system/xbin").mkdirs()
File("$workdir/root/system/lib").mkdirs()

xbins.forEach { it ->
    val bin = System.getenv("ANDROID_PRODUCT_OUT") + "/system/xbin/" + it
    val binTgt = workdir + "/root/system/xbin/" + it
    println("$bin -> $binTgt")
    File(bin).copyTo(File(binTgt), true)
}

bins.forEach { it ->
    val bin = System.getenv("ANDROID_PRODUCT_OUT") + "/system/bin/" + it
    val binTgt = workdir + "/root/system/bin/" + it
    println("$bin -> $binTgt")
    File(bin).copyTo(File(binTgt), true)
}

libs.forEach { it ->
    val lib = System.getenv("ANDROID_PRODUCT_OUT") + "/system/lib/" + it
    val libTgt = workdir + "/root/system/lib/" + it
    println("$lib -> $libTgt")
    File(lib).copyTo(File(libTgt), true)
}

toolboxLinks.forEach { it ->
    val bin = workdir + "/root/system/bin/" + it
    Files.deleteIfExists(File(bin).toPath())
    Files.createSymbolicLink(Paths.get(bin), Paths.get("toolbox"));
}

toyboxLinks.forEach { it ->
    val bin = workdir + "/root/system/bin/" + it
    Files.deleteIfExists(File(bin).toPath())
    Files.createSymbolicLink(Paths.get(bin), Paths.get("toybox"));
}

File(workdir + "/root/system/etc/init").mkdirs()
initrcFiles.forEach { it ->
    val bin = System.getenv("ANDROID_PRODUCT_OUT") + "/system/etc/init/" + it
    val binTgt = workdir + "/root/system/etc/init/" + it
    Files.deleteIfExists(File(binTgt).toPath())
    File(bin).copyTo(File(binTgt), true)
}

fun enableShell() {
    val bin = "src/resources/console.rc"
    val binTgt = workdir + "/root/system/etc/init/" + "console.rc"
    Files.deleteIfExists(File(binTgt).toPath())
    File(bin).copyTo(File(binTgt), true)
}
enableShell()
