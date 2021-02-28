package cfig.helper

import org.apache.commons.exec.CommandLine
import org.bouncycastle.util.encoders.Hex
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.lang.RuntimeException
import java.util.*

class OpenSslHelper {
    enum class KeyFormat {
        PEM,  //header + metadata + base64 der
        DER // der format
    }

    interface IKey {
        val name: String
        val data: ByteArray
        fun writeTo(fileName: String) {
            FileOutputStream(fileName, false).use {
                it.write(data)
            }
        }
    }

    class PK1Key(val format: KeyFormat = KeyFormat.PEM,
                 override val data: ByteArray = byteArrayOf(),
                 override val name: String = "RSA Private") : IKey {
        /*
            PEM private key -> PEM/DER public key
         */
        fun getPublicKey(pubKeyFormat: KeyFormat): PK1PubKey {
            if (format != KeyFormat.PEM) {
                throw IllegalArgumentException("can not handle $format private key")
            }
            val ret = Helper.powerRun("openssl rsa -in $stdin -pubout -outform ${pubKeyFormat.name}",
                    ByteArrayInputStream(data))
            log.info("privateToPublic:stderr: ${String(ret[1])}")
            return PK1PubKey(format = pubKeyFormat, data = ret[0])
        }

        /*
        file based:
            openssl rsa -in private.pem -pubout -out public.pem
        stream based:
            openssl rsa -in - -pubout
         */
        fun getPk8PublicKey(): Pk8PubKey {
            if (this.format != KeyFormat.PEM) {
                throw java.lang.IllegalArgumentException("Only PEM key is supported")
            }
            val ret = Helper.powerRun2("openssl rsa -in $stdin -pubout",
                    ByteArrayInputStream(data))
            if (ret[0] as Boolean) {
                log.info("getPk8PublicKey:error: ${String(ret[2] as ByteArray)}")
                return Pk8PubKey(KeyFormat.PEM, ret[1] as ByteArray)
            } else {
                log.error("stdout: " + String(ret[1] as ByteArray))
                log.error("stderr: " + String(ret[2] as ByteArray))
                throw RuntimeException()
            }
        }

        /*
            file based:
                openssl pkcs8 -nocrypt -in $(rsa_key) -topk8 -outform DER -out $(pk8_key)
            stream based:
                openssl pkcs8 -nocrypt -in - -topk8 -outform DER
         */
        fun toPk8(pk8Format: KeyFormat): PK8RsaKey {
            val ret = Helper.powerRun("openssl pkcs8 -nocrypt -in $stdin -topk8 -outform ${pk8Format.name}",
                    ByteArrayInputStream(data))
            log.info("toPk8Private:stderr: ${String(ret[1])}")
            return PK8RsaKey(format = pk8Format, data = ret[0])
        }

        fun toCsr(): Csr {
            val info = "/C=CN/ST=Shanghai/L=Shanghai/O=XXX/OU=infra/CN=gerrit/emailAddress=webmaster@XX.com"
            val cmdLine = CommandLine.parse("openssl req -new -key $stdin -subj").apply {
                this.addArgument("$info", true)
            }
            val ret = Helper.powerRun3(cmdLine, ByteArrayInputStream(data))
            if (ret[0] as Boolean) {
                log.info("toCsr:error: ${String(ret[2] as ByteArray)}")
                return Csr(data = ret[1] as ByteArray)
            } else {
                log.error("stdout: " + String(ret[1] as ByteArray))
                log.error("stderr: " + String(ret[2] as ByteArray))
                throw RuntimeException()
            }
        }

        fun toV1Cert(): Crt {
            //full command:
            //  openssl x509 -req -in 2017key.csr -signkey 2017key.rsa.pem -out theCert.crt -days 180
            //send RSA key as input stream:
            //  openssl x509 -req -in 2017key.csr -signkey - -out theCert.crt -days 180
            //send RSA key as input stream, crt as output stream:
            //  openssl x509 -req -in 2017key.csr -signkey - -days 180
            val csr = this.toCsr()
            val tmpFile = File.createTempFile("pk1.", ".csr")
            tmpFile.writeBytes(csr.data)
            tmpFile.deleteOnExit()
            val ret = Helper.powerRun2("openssl x509 -req -in ${tmpFile.path} -signkey $stdin -days 180",
                    ByteArrayInputStream(data))
            if (ret[0] as Boolean) {
                log.info("toCrt:error: ${String(ret[2] as ByteArray)}")
                return Crt(ret[1] as ByteArray)
            } else {
                log.error("stdout: " + String(ret[1] as ByteArray))
                log.error("stderr: " + String(ret[2] as ByteArray))
                throw RuntimeException()
            }
        }

        companion object {
            /*
                -> PEM RSA private key
             */
            fun generate(keyLength: Int): PK1Key {
                val ret = Helper.powerRun("openssl genrsa $keyLength", null)
                log.info("generateRSA:stderr: ${String(ret[1])}")
                return PK1Key(format = KeyFormat.PEM, data = ret[0])
            }
        }
    }

    class PK8RsaKey(val format: KeyFormat = KeyFormat.PEM,
                    override val data: ByteArray = byteArrayOf(),
                    override val name: String = "PK8 Private") : IKey {

        /*
        file based:
            openssl pkcs8 -nocrypt -in $(pk8_key) -inform DER -out $(rsa_key).converted.tmp
            openssl rsa -in $(rsa_key).converted.tmp -out $(rsa_key).converted
        stream based:
            openssl pkcs8 -nocrypt -in - -inform DER
            openssl rsa -in - -out -
         */
        fun toPk1(): PK1Key {
            if (this.format != KeyFormat.PEM) {
                throw IllegalArgumentException("Only pk8+pem can be converted to RSA")
            }
            val ret = Helper.powerRun2("openssl rsa -in $stdin",
                    ByteArrayInputStream(data))
            if (ret[0] as Boolean) {
                log.info("toRsaPrivate:error: ${String(ret[2] as ByteArray)}")
                return PK1Key(KeyFormat.PEM, ret[1] as ByteArray)
            } else {
                log.error("stdout: " + String(ret[1] as ByteArray))
                log.error("stderr: " + String(ret[2] as ByteArray))
                throw RuntimeException()
            }
        }

        /*
            openssl pkcs8 -nocrypt -in - -inform DER
         */
        fun transform(inFormat: KeyFormat, outFormat: KeyFormat): PK8RsaKey {
            val ret = Helper.powerRun2("openssl pkcs8 -nocrypt -in $stdin -inform ${inFormat.name} -outform ${outFormat.name}",
                    ByteArrayInputStream(data))
            if (ret[0] as Boolean) {
                log.info("transform:error: ${String(ret[2] as ByteArray)}")
                return PK8RsaKey(data = ret[1] as ByteArray)
            } else {
                log.error("stdout: " + String(ret[1] as ByteArray))
                log.error("stderr: " + String(ret[2] as ByteArray))
                throw IllegalArgumentException()
            }
        }

        /*
        file based:
            openssl rsa -in pkcs8.pem -pubout -out public_pkcs8.pem
        stream based:
            openssl rsa -in - -pubout
         */
        fun getPublicKey(): Pk8PubKey {
            if (this.format != KeyFormat.PEM) {
                throw java.lang.IllegalArgumentException("Only PEM key is supported")
            }
            val ret = Helper.powerRun2("openssl rsa -in $stdin -pubout",
                    ByteArrayInputStream(data))
            if (ret[0] as Boolean) {
                log.info("getPublicKey:error: ${String(ret[2] as ByteArray)}")
                return Pk8PubKey(KeyFormat.PEM, ret[1] as ByteArray)
            } else {
                log.error("stdout: " + String(ret[1] as ByteArray))
                log.error("stderr: " + String(ret[2] as ByteArray))
                throw RuntimeException()
            }
        }
    }

    class PK1PubKey(
            val format: KeyFormat = KeyFormat.PEM,
            override val data: ByteArray = byteArrayOf(),
            override val name: String = "RSA Public"
    ) : IKey

    class Pk8PubKey(
            val format: KeyFormat = KeyFormat.PEM,
            override val data: ByteArray = byteArrayOf(),
            override val name: String = "Pk8 Public"
    ) : IKey

    class Csr(override val name: String = "CSR", override val data: ByteArray = byteArrayOf()) : IKey
    class Jks(override val name: String = "Java Keystore", override val data: ByteArray = byteArrayOf()) : IKey {
        //keytool -list -v -deststorepass $(thePassword) -keystore $(jks_file)
        fun check(passWord: String = "somepassword") {
            val tmpFile = File.createTempFile("tmp.", ".jks").apply { this.deleteOnExit() }
            tmpFile.writeBytes(this.data)
            val ret = Helper.powerRun2("keytool -list -v -deststorepass $passWord -keystore $tmpFile",
                    null)
            if (ret[0] as Boolean) {
                log.info("Jks.check:stdout: ${String(ret[1] as ByteArray)}")
                log.info("Jks.check:error: ${String(ret[2] as ByteArray)}")
            } else {
                log.error("stdout: " + String(ret[1] as ByteArray))
                log.error("stderr: " + String(ret[2] as ByteArray))
                throw RuntimeException()
            }
        }
    }

    class Crt(val data: ByteArray = byteArrayOf()) {
        //Result: trustedCertEntry
        //keytool -importcert -file 2017key.crt -deststorepass somepassword -srcstorepass somepassword -keystore 2017key.2.jks
        fun toJks(paramSrcPass: String = "somepassword", paramDstPass: String = "somepassword"): Jks {
            val crtFile = File.createTempFile("tmp.", ".crt").apply { this.deleteOnExit() }
            crtFile.writeBytes(this.data)
            val outFile = File.createTempFile("tmp.", ".jks").apply { this.delete() }
            val ret = Helper.powerRun2("keytool -importcert -file ${crtFile.path}" +
                    " -deststorepass $paramDstPass -srcstorepass $paramSrcPass " +
                    " -keystore ${outFile.path}",
                    ByteArrayInputStream("yes\n".toByteArray()))
            if (ret[0] as Boolean) {
                log.info("toJks:error: ${String(ret[2] as ByteArray)}")
                log.info("toJks:stdout: ${String(ret[1] as ByteArray)}")
            } else {
                log.error("stdout: " + String(ret[1] as ByteArray))
                log.error("stderr: " + String(ret[2] as ByteArray))
                throw RuntimeException()
            }

            if (!outFile.exists()) {
                throw RuntimeException()
            }
            val outData = outFile.readBytes()
            outFile.delete()
            return Jks(data = outData)
        }
    }

    class Pfx(override val name: String = "androiddebugkey",
              var thePassword: String = "somepassword",
              override var data: ByteArray = byteArrayOf()) : IKey {
        fun generate(pk1: PK1Key, crt: Crt) {
            val pk1File = File.createTempFile("tmp.", ".file").apply { this.deleteOnExit() }
            pk1File.writeBytes(pk1.data)

            val crtFile = File.createTempFile("tmp.", ".file").apply { this.deleteOnExit() }
            crtFile.writeBytes(crt.data)

            //openssl pkcs12 -export -out $(pfx_cert) -inkey $(rsa_key) -in $(crt_file) -password pass:$(thePassword) -name $(thePfxName)
            val cmd = "openssl pkcs12 -export " +
                    " -inkey ${pk1File.path} " +
                    " -in ${crtFile.path} " +
                    " -password pass:${this.thePassword} -name ${this.name}"
            val ret = Helper.powerRun2(cmd, null)
            if (ret[0] as Boolean) {
                log.info("toPfx:error: ${String(ret[2] as ByteArray)}")
                log.info("toPfx:stdout: ${Hex.toHexString(ret[1] as ByteArray)}")
                this.data = ret[1] as ByteArray
            } else {
                log.error("stdout: " + String(ret[1] as ByteArray))
                log.error("stderr: " + String(ret[2] as ByteArray))
                throw RuntimeException()
            }
        }

        //Zkeytool -importkeystore -deststorepass $(thePassword) -destkeystore $(jks_file) -srckeystore $(pfx_cert) -srcstoretype PKCS12 -srcstorepass $(thePassword)
        fun toJks(): Jks {
            val jksFile = File.createTempFile("tmp.", ".file").apply { this.delete() }
            val thisFile = File.createTempFile("tmp.", ".file").apply { this.deleteOnExit() }
            thisFile.writeBytes(this.data)
            val cmd = "keytool -importkeystore " +
                    " -srcstorepass $thePassword -deststorepass $thePassword " +
                    " -destkeystore ${jksFile.path} " +
                    " -srckeystore $thisFile -srcstoretype PKCS12"
            val ret = Helper.powerRun2(cmd, null)
            if (ret[0] as Boolean) {
                log.info("toJks:error: " + String(ret[2] as ByteArray))
                log.info("toJks:stdout: " + String(ret[1] as ByteArray))
                this.data = ret[1] as ByteArray
            } else {
                log.error("stdout: " + String(ret[1] as ByteArray))
                log.error("stderr: " + String(ret[2] as ByteArray))
                throw RuntimeException()
            }
            val outDate = jksFile.readBytes()
            jksFile.delete()
            return Jks(this.name, outDate)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(OpenSslHelper::class.java)
        val stdin = if (System.getProperty("os.name").contains("Mac")) "/dev/stdin" else "-"

        fun decodePem(keyText: String): ByteArray {
            val publicKeyPEM = keyText
                    .replace("-----BEGIN .*-----".toRegex(), "")
                    .replace(System.lineSeparator().toRegex(), "")
                    .replace("\n", "")
                    .replace("\r", "")
                    .replace("-----END .*-----".toRegex(), "")
            return Base64.getDecoder().decode(publicKeyPEM)
        }

        fun toPfx(password: String = "somepassword", keyName: String = "androiddebugkey", pk1: PK1Key, crt: Crt) {
            val pk1File = File.createTempFile("tmp.", ".file").apply { this.deleteOnExit() }
            pk1File.writeBytes(pk1.data)

            val crtFile = File.createTempFile("tmp.", ".file").apply { this.deleteOnExit() }
            crtFile.writeBytes(crt.data)

            //openssl pkcs12 -export -out $(pfx_cert) -inkey $(rsa_key) -in $(crt_file) -password pass:$(thePassword) -name $(thePfxName)
            val cmd = "openssl pkcs12 -export -inkey ${pk1File.path} -in ${crtFile.path} -password pass:$password -name $keyName"
            val ret = Helper.powerRun2(cmd, null)
            if (ret[0] as Boolean) {
                log.info("toPfx:error: ${String(ret[2] as ByteArray)}")
                log.info("toPfx:stdout: ${Hex.toHexString(ret[1] as ByteArray)}")
            } else {
                log.error("stdout: " + String(ret[1] as ByteArray))
                log.error("stderr: " + String(ret[2] as ByteArray))
                throw RuntimeException()
            }
        }
    }
}
