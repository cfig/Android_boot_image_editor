package cfig.helper

import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.slf4j.LoggerFactory

class AndroidHelper {
    companion object {
        private val log = LoggerFactory.getLogger(AndroidHelper::class.java)

        fun signFile(signer: String,
                     inFile: String,
                     outFile: String,
                     pemKey: String = "aosp/security/testkey.x509.pem",
                     pk8Key: String = "aosp/security/testkey.pk8") {
            var cmd = "java -Xmx2048m -jar $signer "
            cmd += " -w "
            cmd += " $pemKey "
            cmd += " $pk8Key "
            cmd += " $inFile "
            cmd += " $outFile "
            log.info("signFile: $cmd")
            DefaultExecutor().execute(CommandLine.parse(cmd))
        }
    }
}
