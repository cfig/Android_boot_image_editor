// Copyright 2021-2024 yuyezhong@gmail.com
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

package cfig.utils

import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.apache.commons.exec.PumpStreamHandler
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import kotlin.system.exitProcess

class EnvironmentVerifier {
    val hasXz: Boolean
        get() : Boolean {
            try {
                val outputStream = ByteArrayOutputStream()
                val exec = DefaultExecutor()
                exec.streamHandler = PumpStreamHandler(outputStream)
                exec.execute(CommandLine.parse("xz --version"))
                val os = outputStream.toString().trim()
                log.debug(os)
                log.debug("xz available")
            } catch (e: Exception) {
                log.warn("'xz' not installed. Please install it manually to analyze DTB files")
                if (isMacOS) {
                    log.warn("For Mac OS: \n\n\tbrew install xz\n")
                }
                return false
            }
            return true
        }

    val hasGzip: Boolean
        get(): Boolean {
            try {
                Runtime.getRuntime().exec(arrayOf("gzip", "-V"), null, null)
                log.debug("gzip available")
            } catch (e: Exception) {
                log.warn("gzip unavailable")
                return false
            }
            return true
        }

    val hasXzcat: Boolean
        get(): Boolean {
            var ret = false
            try {
                val process = Runtime.getRuntime().exec(arrayOf("xzcat", "-V"), null, null)
                log.debug("xzcat available")
                val exitCode = process.waitFor()
                ret = (exitCode == 0 && process.exitValue() == 0)
            } catch (e: Exception) {
                log.warn("xzcat unavailable")
            }
            return ret
        }

    val hasBzcat: Boolean
        get(): Boolean {
            var ret = false
            try {
                val process = Runtime.getRuntime().exec(arrayOf("bzcat", "-V"), null, null)
                log.debug("bzcat available")
                val exitCode = process.waitFor()
                ret = (exitCode == 0 && process.exitValue() == 0)
            } catch (e: Exception) {
                log.warn("bzcat unavailable")
            }
            return ret
        }

    val has7z: Boolean
        get(): Boolean {
            try {
                Runtime.getRuntime().exec("7z i")
                log.debug("7z available")
            } catch (e: Exception) {
                log.warn("7z not installed")
                return false
            }
            return true
        }

    val hasDtc: Boolean
        get(): Boolean {
            try {
                Runtime.getRuntime().exec("dtc --version")
                log.debug("dtc available")
            } catch (e: Exception) {
                log.warn("'dtc' not installed. Please install it manually to analyze DTB files")
                if (isMacOS) {
                    log.warn("For Mac OS: \n\tbrew install dtc\n")
                } else if (isLinux) {
                    log.warn("Like this: \n\t$ sudo apt install device-tree-compiler")
                }
                return false
            }
            return true
        }

    val isMacOS: Boolean
        get() = System.getProperty("os.name").contains("Mac")

    val isLinux: Boolean
        get() = System.getProperty("os.name").contains("Linux")

    val isWindows: Boolean
        get() = System.getProperty("os.name").contains("Windows")

    var lz4prog: String = "lz4"

    private fun getJavaVersion(): Int {
        return System.getProperty("java.version").let { version ->
            if (version.startsWith("1.")) {
                version.substring(2, 3)
            } else {
                val dot = version.indexOf(".")
                if (dot != -1) {
                    version.substring(0, dot)
                } else {
                    version
                }
            }
        }.toInt()
    }

    init {
        if (getJavaVersion() < 11) {
            log.error("Java 11+ is required, while it's " + System.getProperty("java.version"))
            exitProcess(1)
        } else {
            log.debug("Java version " + System.getProperty("java.version"))
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger("EnvironmentVerifier")
    }
}
