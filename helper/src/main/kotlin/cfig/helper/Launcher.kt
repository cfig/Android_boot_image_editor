package cfig.helper

import com.google.common.io.Files
import org.bouncycastle.util.io.pem.PemReader
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStreamReader
import kotlin.system.exitProcess
import cfig.helper.OpenSslHelper.KeyFormat

class Launcher {
    companion object {
        fun help() {
            println("Help:")
            println("\tcrypo.list")
            println("\tcrypto.key.parse <file>")
            println("\tcrypto.key.genrsa <key_len> <out>")
            println("\tcrypto.key.1 <file>")
        }
    }
}

fun main(args: Array<String>) {
    val log = LoggerFactory.getLogger("main")
    if (args.isEmpty()) {
        Launcher.help()
        exitProcess(0)
    }

    when (args[0]) {
        "crypo.list" -> {
            CryptoHelper.listAll()
        }
        "crypto.key.parse" -> {
            val ba = File(args[1]).readBytes()
            val k = CryptoHelper.KeyBox.parse(ba)
            if (k is Boolean) {
                if (k) {
                    log.info("File recognized but not parsed")
                } else {
                    log.warn("Unrecognized file " + args[1])
                }
            } else {
                log.info("Recognized " + k::class)
            }
        }
        "crypto.key.genrsa", "crypto.key.0" -> {
            val kLen: Int = args[1].toInt()
            val kOut = args[2]
            OpenSslHelper.PK1Key.generate(kLen).apply {
                writeTo(kOut)
            }
            log.info("RSA key(len=$kLen) written to $kOut")
        }
        "crypto.key.1" -> {
            //Action-1: private RSA key -> RSA public key(PEM)
            val hint = "private RSA key -> RSA public key(PEM)"
            val kFile = args[1]
            val outFile = args[2]
            assert(CryptoHelper.KeyBox.parse(File(kFile).readBytes()) is org.bouncycastle.asn1.pkcs.RSAPrivateKey)
            val rsa = OpenSslHelper.PK1Key(data = File(kFile).readBytes())
            val rsaPubPEM = rsa.getPublicKey(KeyFormat.PEM).apply {
                writeTo(outFile)
                log.info("$hint: $kFile => $outFile")
            }
        }
        "crypto.key.2" -> {
            //Action-2: private RSA key -> RSA public key(DER)
            val kFile = args[1]
            val outFile = args[2]
            assert(CryptoHelper.KeyBox.parse(File(kFile).readBytes()) is org.bouncycastle.asn1.pkcs.RSAPrivateKey)
            val rsa = OpenSslHelper.PK1Key(data = File(kFile).readBytes())
            val rsaPubDer = rsa.getPublicKey(KeyFormat.DER).apply {
                writeTo(outFile)
                log.info("RSA pub key(der) written to $outFile")
            }
        }
        "crypto.key.3" -> {
            //Action-3: (PEM) -->  (DER)
            val kFile = args[1]
            val outFile = args[2]
            val decodeFromPem = CryptoHelper.KeyBox.decodePem(File(kFile).readText())
            Files.write(decodeFromPem, File(outFile))
            log.info("PEM ($kFile) => raw ($outFile)")
        }
        "crypto.key.4" -> {
            //Action-4:
            var hint = "RSA private: PK1 <=> PK8(PEM)"
            val kFile = args[1]
            val outFile = args[2]
            when (val k = CryptoHelper.KeyBox.parse(File(kFile).readBytes())) {
                is org.bouncycastle.asn1.pkcs.RSAPrivateKey -> {
                    hint = "RSA private: PK1 => PK8(PEM)"
                    val rsa = OpenSslHelper.PK1Key(data = File(kFile).readBytes())
                    rsa.toPk8(KeyFormat.PEM).writeTo(outFile)
                }
                is java.security.interfaces.RSAPrivateKey -> {
                    hint = "RSA private: PK8 => PK1(PEM)"
                    val rsa = OpenSslHelper.PK8RsaKey(data = File(kFile).readBytes())
                    rsa.toPk1().writeTo(outFile)
                }
                else -> {
                    hint = "RSA private: PK1 <=> PK8(PEM)"
                    log.warn(hint)
                    throw IllegalArgumentException("unsupported $k")
                }
            }
            log.info("$hint: $kFile => $outFile")
        }
        "crypto.key.5" -> {
            val hint = "RSA private(PK8): => Public Key(PK8, PEM)"
            val kFile = args[1]
            val outFile = args[2]
            assert(CryptoHelper.KeyBox.parse(File(kFile).readBytes()) is org.bouncycastle.asn1.pkcs.RSAPrivateKey)
            val pk8rsa = OpenSslHelper.PK8RsaKey(KeyFormat.PEM, File(kFile).readBytes())
            pk8rsa.getPublicKey().writeTo(outFile)
            log.info("$hint: $kFile => $outFile")
        }
        "crypto.key.7" -> {
            val hint = "RSA private: PK1 => PK8(DER)"
            val kFile = args[1]
            val outFile = args[2]
            assert(CryptoHelper.KeyBox.parse(File(kFile).readBytes()) is org.bouncycastle.asn1.pkcs.RSAPrivateKey)
            val rsa = OpenSslHelper.PK1Key(data = File(kFile).readBytes())
            rsa.toPk8(KeyFormat.DER).writeTo(outFile)
            log.info("$hint: $kFile => $outFile")
        }
        "crypto.key.10" -> {
            //Action-10:
            var hint = ""
            val kFile = args[1]
            val outFile = args[2]
            val inBytes = File(kFile).readBytes()
            assert(CryptoHelper.KeyBox.parse(inBytes) is java.security.interfaces.RSAPrivateKey)
            val p = PemReader(InputStreamReader(ByteArrayInputStream(File(kFile).readBytes()))).readPemObject()
            if (p != null) {//pem
                hint = "PK8 RSA: PEM => DER"
                OpenSslHelper.PK8RsaKey(KeyFormat.PEM, inBytes).transform(KeyFormat.PEM, KeyFormat.DER).writeTo(outFile)
            } else {//der
                hint = "PK8 RSA: DER => PEM"
                OpenSslHelper.PK8RsaKey(KeyFormat.DER, inBytes).transform(KeyFormat.DER, KeyFormat.PEM).writeTo(outFile)
            }
            log.info("$hint: $kFile => $outFile")
        }
        "crypto.key.11" -> {
            val hint = "RSA public(PK8): PEM => DER"
            val kFile = args[1]
            val outFile = args[2]
            File(outFile).writeBytes(
                CryptoHelper.KeyBox.decodePem(File(kFile).readText())
            )
            log.info("$hint: $kFile => $outFile")
        }
        "crypto.key.csr" -> {
            //Action-xx:
            val hint = "PK1 RSA PEM ==> CSR"
            val kFile = args[1]
            val outFile = args[2]
            val inBytes = File(kFile).readBytes()
            assert(CryptoHelper.KeyBox.parse(inBytes) is org.bouncycastle.asn1.pkcs.RSAPrivateKey)
            OpenSslHelper.PK1Key(KeyFormat.PEM,inBytes).toCsr().writeTo(outFile)
            log.info("$hint: $kFile => $outFile")
        }
        "crypto.key.crt" -> {
            //Action-xx:
            val hint = "PK1 RSA PEM ==> CRT"
            val kFile = args[1]
            val outFile = args[2]
            val inBytes = File(kFile).readBytes()
            assert(CryptoHelper.KeyBox.parse(inBytes) is org.bouncycastle.asn1.pkcs.RSAPrivateKey)
            OpenSslHelper.PK1Key(KeyFormat.PEM,inBytes).toV1Cert().writeTo(outFile)
            log.info("$hint: $kFile => $outFile")
        }
        "crypto.key.22" -> {
            //Action-xx:
            val hint = "CRT ==> JKS"
            val kFile = args[1]
            val outFile = args[2]
            val inBytes = File(kFile).readBytes()
            assert(CryptoHelper.KeyBox.parse(inBytes) is org.bouncycastle.asn1.pkcs.RSAPrivateKey)
            OpenSslHelper.PK1Key(KeyFormat.PEM,inBytes).toV1Cert().writeTo(outFile)
            log.info("$hint: $kFile => $outFile")
        }
        "crypto.key.xx" -> {
            //Action-xx:
            val hint = ""
            val kFile = args[1]
            val outFile = args[2]
            File(outFile).writeBytes(
                CryptoHelper.KeyBox.decodePem(File(kFile).readText())
            )
            log.info("$hint: $kFile => $outFile")
        }
        else -> {
            Launcher.help()
            exitProcess(1)
        }
    }
    return
}
