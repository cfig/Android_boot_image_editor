package cfig

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
                Runtime.getRuntime().exec("gzip -V")
                log.debug("gzip available")
            } catch (e: Exception) {
                log.warn("gzip unavailable")
                return false
            }
            return true
        }

    val hasLz4: Boolean
        get() : Boolean {
            try {
                Runtime.getRuntime().exec("lz4 --version")
                log.debug("lz4 available")
            } catch (e: Exception) {
                log.warn("lz4 not installed")
                if (isMacOS) {
                    log.warn("For Mac OS: \n\n\tbrew install lz4\n")
                }
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
                    log.warn("For Mac OS: \n\n\tbrew install dtc\n")
                } else {
                    log.warn("Like this: \n\n\t$ sudo apt install device-tree-compiler")
                }
                return false
            }
            return true
        }

    val isMacOS: Boolean
        get() = System.getProperty("os.name").contains("Mac")

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
        if (getJavaVersion() < 9) {
            log.error("Java 9+ is required, while it's " + System.getProperty("java.version"))
            exitProcess(1)
        } else {
            log.debug("Java version " + System.getProperty("java.version"))
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger("EnvironmentVerifier")
    }
}
