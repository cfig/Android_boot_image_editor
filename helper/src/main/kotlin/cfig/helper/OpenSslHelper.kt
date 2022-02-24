package cfig.helper

import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.slf4j.LoggerFactory
import java.io.File

//https://www.digicert.com/kb/ssl-support/openssl-quick-reference-guide.htm
class OpenSslHelper {
    companion object {
        private val log = LoggerFactory.getLogger(OpenSslHelper::class.java)

        //openssl req -new -newkey rsa:2048 -nodes -keyout server.key -out server.csr -subj "/C=US/ST=Utah/L=Lehi/O=Your Company, Inc./OU=IT/CN=yourdomain.com"\n
        //openssl req -text -in server.csr -noout -verify
        //\"/C=US/ST=Utah/L=Lehi/O=Your Company, Inc./OU=IT/CN=yourdomain.com\"
        fun createCsr(outKey: String, outCsr: String, subj: String, keyLen: Int = 2048) {
            DefaultExecutor().execute(
                CommandLine.parse("openssl req -new -newkey rsa:$keyLen -nodes").apply {
                    addArguments("-keyout $outKey")
                    addArguments("-out $outCsr")
                    addArgument("-subj").addArgument("$subj", false)
                })
            DefaultExecutor().execute(CommandLine.parse("openssl req -text -in $outCsr -noout -verify"))
        }

        fun toJks(
            pk8: String, x509Pem: String,
            outFile: String,
            paramSrcPass: String = "somepassword",
            paramDstPass: String = "somepassword",
            alias: String = "androiddebugkey"
        ) {
            File(outFile).let {
                if (it.exists()) it.delete()
            }
            val privKey = File.createTempFile("key.", ".tmp").let {
                it.deleteOnExit()
                val ret = Helper.powerRun2("openssl pkcs8 -in $pk8 -inform DER -outform PEM -nocrypt", null)
                if (ret[0] as Boolean) {
                    it.writeBytes(ret[1] as ByteArray)
                } else {
                    log.error("stdout: " + String(ret[1] as ByteArray))
                    log.error("stderr: " + String(ret[2] as ByteArray))
                    throw java.lang.RuntimeException()
                }
                it
            }

            val pk12 = File.createTempFile("key.", ".tmp").let {
                it.deleteOnExit()
                val ret = Helper.powerRun2(
                    "openssl pkcs12 -export -in $x509Pem -password pass:$paramSrcPass -inkey ${privKey.path} -name androiddebugkey",
                    null
                )
                if (ret[0] as Boolean) {
                    it.writeBytes(ret[1] as ByteArray)
                } else {
                    log.error("stdout: " + String(ret[1] as ByteArray))
                    log.error("stderr: " + String(ret[2] as ByteArray))
                    throw java.lang.RuntimeException()
                }
                it
            }

            val ret = Helper.powerRun2(
                "keytool -importkeystore " +
                        " -deststorepass $paramDstPass -destkeystore $outFile" +
                        " -srckeystore ${pk12.path} -srcstoretype PKCS12 -srcstorepass $paramSrcPass" +
                        " -alias $alias",
                null
            )
            if (ret[0] as Boolean) {
                log.info("$outFile is ready")
            } else {
                log.error("stdout: " + String(ret[1] as ByteArray))
                log.error("stderr: " + String(ret[2] as ByteArray))
                throw java.lang.RuntimeException()
            }
        }
    }
}