// Copyright 2022 yuyezhong@gmail.com
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
package cc.cfig.droid.ota

import org.apache.commons.compress.archivers.zip.ZipFile
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.*

class Common {
    companion object {
        private val log = LoggerFactory.getLogger(Common::class.java)
        private val PARTITIONS_WITH_CARE_MAP =
            mutableListOf("system", "vendor", "product", "system_ext", "odm", "vendor_dlkm", "odm_dlkm")
        val PARTITIONS_WITH_BUILD_PROP = PARTITIONS_WITH_CARE_MAP.apply { add("boot") }

        private fun getEntryStream(zipFile: ZipFile, entryName: String): InputStream {
            return zipFile.getInputStream(zipFile.getEntry(entryName))
        }

        fun Properties.getBuildProp(k: String): String? {
            return (this.get("build.prop") as Properties).getProperty(k)
        }

        fun loadInfoDict(fileName: String): Properties {
            val d = Properties()
            ZipFile(fileName).use { zf ->
                log.info("loading META/misc_info.txt ...")
                //1: misc_info.txt
                d.load(getEntryStream(zf, "META/misc_info.txt"))
                if (null == d.getProperty("recovery_api_version")) {
                    throw IllegalArgumentException("Failed to find 'recovery_api_version'")
                }
                if (null == d.getProperty("fstab_version")) {
                    throw IllegalArgumentException("Failed to find 'fstab_version'")
                }
                if ("true" == d.getProperty("system_root_image")) {
                    throw IllegalArgumentException("BOARD_BUILD_SYSTEM_ROOT_IMAGE no longer supported")
                }
                val recoveryFstabPath = "BOOT/RAMDISK/system/etc/recovery.fstab"
                val recoveryFstabPath2 = "VENDOR_BOOT/RAMDISK/system/etc/recovery.fstab"
                val validFstab = if (zf.getEntry(recoveryFstabPath) != null) {
                    recoveryFstabPath
                } else {
                    recoveryFstabPath2
                }
                //2: .fstab
                d.put("fstab", loadRecoveryFstab(zf, validFstab, false))

                //load all build.prop
                PARTITIONS_WITH_BUILD_PROP.forEach { part ->
                    val subProps = Properties()
                    if (part == "boot") {
                        arrayOf("BOOT/RAMDISK/system/etc/ramdisk/build.prop",
                            "BOOT/RAMDISK/prop.default").forEach { bootBuildProp ->
                            zf.getEntry(bootBuildProp)?.let { entry ->
                                log.info("loading /$bootBuildProp ...")
                                subProps.load(zf.getInputStream(entry))
                            }
                        }
                    } else {
                        zf.getEntry("${part.uppercase(Locale.getDefault())}/build.prop")?.let { entry ->
                            log.info("loading /$part/build.prop ...")
                            subProps.load(zf.getInputStream(entry))
                        }
                        zf.getEntry("${part.uppercase(Locale.getDefault())}/etc/build.prop")?.let { entry ->
                            log.info("loading /$part/etc/build.prop ...")
                            subProps.load(zf.getInputStream(entry))
                        }
                    }
                    //3: .$part.build.prop
                    d.put("$part.build.prop", subProps)
                }
                //4: .build.prop == .system.build.prop
                log.info("duplicating system.build.prop -> build.prop")
                d.put("build.prop", d.get("system.build.prop"))
            }
            if (d.get("avb_enable") == "true") {
                // 5: avb related
                (d.get("build.prop") as Properties).let { buildprop ->
                    var fp: String?
                    fp = buildprop.get("ro.build.fingerprint") as String?
                    if (fp == null) {
                        fp = buildprop.get("ro.build.thumbprint") as String?
                    }
                    fp?.let {
                        log.warn("adding avb_salt from fingerprint ...")
                        d.put("avb_salt", "fp")
                    }
                }
            }
            return d
        }

        private fun loadRecoveryFstab(zf: ZipFile, fstabPath: String, bSystemRoot: Boolean = false) {
            class Partition(
                var mount_point: String = "",
                var fs_type: String = "",
                var device: String = "",
                var length: Long = 0,
                var selinuxContext: String = "",
            )
            log.info("loading $fstabPath ...")
            val ret: MutableMap<String, Partition> = mutableMapOf()
            val rs = getEntryStream(zf, fstabPath).readBytes().toString(StandardCharsets.UTF_8)
            log.debug(rs)
            rs.lines().forEach rs@{ line ->
                val item = line.trim()
                if (item.isEmpty() || item.startsWith("#")) {
                    log.debug("ignore empty/comment line")
                    return@rs
                }
                val pieces = item.split("\\s+".toRegex())
                if (pieces.size != 5) {
                    throw IllegalArgumentException("malformed recovery.fstab line: [$item]")
                }
                if (pieces[4].contains("voldmanaged=")) {
                    log.info("Ignore entries that are managed by vold: [$item]")
                    return@rs
                }
                val lengthOption = pieces[4].split(",").filter { it.startsWith("length=") }
                val length = when (lengthOption.size) {
                    0 -> 0
                    1 -> lengthOption[0].substring(7).toLong()
                    else -> throw IllegalArgumentException("multiple 'length=' in options")
                }

                val mountFlags = pieces[3]
                val mountContextFlags = mountFlags.split(",").filter { it.startsWith("context=") }
                val context = if (mountContextFlags.size == 1) mountContextFlags[0] else ""

                ret.put(pieces[1], Partition(pieces[1], pieces[2], pieces[0], length, context))
            }
            if (bSystemRoot) {
                if (ret.keys.contains("/system") || !ret.keys.contains("/")) {
                    throw IllegalArgumentException("not allowed")
                }
                val systemPartition = ret.get("/") as Partition
                systemPartition.mount_point = "/"
                log.info("adding /system for system_as_root devices")
                ret.put("/system", systemPartition)
            }
        }
    }
}
