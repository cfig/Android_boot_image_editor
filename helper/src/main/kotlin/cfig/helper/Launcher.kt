@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package cfig.helper

import cfig.helper.OpenSslHelper.KeyFormat
import org.bouncycastle.util.io.pem.PemReader
import org.slf4j.LoggerFactory
import java.io.*
import java.security.cert.Certificate
import java.util.*
import kotlin.system.exitProcess

class Launcher {
    companion object {
        private const val hint1 = "private RSA key -> RSA public key(PEM)"
        private const val hint2 = "private RSA key -> RSA public key(DER)"
        private const val hint3 = "(PEM) -->  (DER)"
        private const val hint4 = "RSA private: PK1 <=> PK8(PEM)"
        private const val hint5 = "RSA private(PK8): => Public Key(PK8, PEM)"
        private const val hint7 = "RSA private: PK1 => PK8(DER)"
        private const val hint10 = "PK8 RSA: PEM <=> DER"
        private const val hint11 = "RSA public(PK8): PEM => DER"
        private const val hintCsr = "PK1 RSA PEM ==> CSR"
        private const val hintCrt = "PK1 RSA PEM ==> CRT"
        private const val hint22 = "CRT ==> JKS"
        private const val hintJks = "Generate JKS"
        val allSupportedCommands =
            listOf("genrsa", "toPublicKey", "decodePEM", "toPk8", "toPk1", "toJks", "toCsr", "toCrt").toString()

        private val log = LoggerFactory.getLogger(Launcher::class.java)

        fun help() {
            println("Help:")
            println("\tcrypo.list")
            println("\tcrypto.key.parse <file>")
            println("\tcrypto.key.0     : <key_len> <out>")
            println("\tcrypto.key.1     : $hint1")
            println("\tcrypto.key.2     : $hint2")
            println("\tcrypto.key.3     : $hint3")
            println("\tcrypto.key.4     : $hint4")
            println("\tcrypto.key.5     : $hint5")
            println("\tcrypto.key.7     : $hint7")
            println("\tcrypto.key.10    : $hint10")
            println("\tcrypto.key.11    : $hint11")
            println("\tcrypto.key.22    : $hint22")
            println("\tcrypto.key.csr   : $hintCsr")
            println("\tcrypto.key.21    : $hintCrt")
            println("\tcrypto.key.jks   : $hintJks")
        }

        fun getSize(size: Long): String? {
            val kilo = 1024L
            val mega = kilo * kilo
            val giga = mega * kilo
            val tera = giga * kilo
            var s = ""
            val kb: Double = size.toDouble() / kilo
            val mb: Double = kb / kilo
            val gb: Double = mb / kilo
            val tb: Double = gb / kilo
            if (size < kilo) {
                s = "$size Bytes"
            } else if (size in kilo until mega) {
                s = String.format("%.2f", kb) + " KB"
            } else if (size in mega until giga) {
                s = String.format("%.2f", mb) + " MB"
            } else if (size in giga until tera) {
                s = String.format("%.2f", gb) + " GB"
            } else if (size >= tera) {
                s = String.format("%.2f", tb) + " TB"
            }
            return s
        }
    }

    fun processCommand(inFile: String, info2: Properties) {
        FileInputStream(inFile).use { fis ->
            info2.load(fis)
        }
        log.warn("CMD=" + info2.getProperty("cmd"))
        val kFile = info2.getProperty("file")
        when (val cmd = info2.getProperty("cmd")) {
            "genrsa" -> {
                val kLen = info2.getProperty("rsa.len").toInt()
                OpenSslHelper.PK1Key.generate(kLen).apply {
                    writeTo(kFile)
                }
                log.info("RSA key(len=$kLen) written to $kFile")
            }
            "toPublicKey" -> {
                val k = CryptoHelper.KeyBox.parse2(File(kFile).readBytes()) as Array<*>
                val bSuccess = k[0] as Boolean
                val bType = k[1] as String
                var outFile = File(kFile).name + ".pub"
                when (k[2]) {
                    is org.bouncycastle.asn1.pkcs.RSAPrivateKey -> {
                        val rsa = OpenSslHelper.PK1Key(data = File(kFile).readBytes())
                        run { //action 1
                            val hint = "private RSA key -> RSA public key(PEM)"
                            outFile = File(kFile).name + ".pem.pub"
                            val rsaPubPEM = rsa.getPublicKey(KeyFormat.PEM).apply {
                                writeTo(outFile)
                                log.info("$hint: $kFile => $outFile")
                            }
                        }

                        run { //action 2
                            val hint = "private RSA key -> RSA public key(DER)"
                            outFile = File(kFile).name + ".der.pub"
                            val rsaPubDer = rsa.getPublicKey(KeyFormat.DER).apply {
                                writeTo(outFile)
                                log.info("$hint: $kFile => $outFile")
                            }
                        }
                    }
                    else -> {
                    }
                }
            }
            "decodePEM" -> {
                val hint = "(PEM) -->  (DER)"
                val outFile = File(kFile).name + ".raw"
                val decodeFromPem = CryptoHelper.KeyBox.decodePem(File(kFile).readText())
                File(outFile).writeBytes(decodeFromPem)
                log.info("$hint: $kFile => $outFile")
            }
            "toCrt" -> {
                val hint = "PK1 RSA PEM ==> CRT"
                val outFile = File(kFile).name + ".crt"
                CryptoHelper.KeyBox.parseToPk8(kFile)
                    .toPk1()
                    .toV1Cert(info2.getProperty("csr.info"))
                    .writeTo(outFile)
                log.info("$hint: $kFile => $outFile")
            }
            "toCsr" -> {
                val hint = "PK1 RSA PEM ==> CSR"
                val outFile = File(kFile).name + ".csr"
                val inBytes = File(kFile).readBytes()
                val k = (CryptoHelper.KeyBox.parse2(inBytes) as Array<*>)[2]
                assert(k is org.bouncycastle.asn1.pkcs.RSAPrivateKey) {
                    "${k!!::class} is not org.bouncycastle.asn1.pkcs.RSAPrivateKey"
                }
                OpenSslHelper.PK1Key(KeyFormat.PEM, inBytes).toCsr(info2.getProperty("csr.info")).writeTo(outFile)
                log.info("$hint: $kFile => $outFile")
            }
            "toJks" -> {
                val hint = "RSA ==> JKS"
                val outFile = File(kFile).name + ".jks"
                val pk8 = CryptoHelper.KeyBox.parseToPk8(kFile)
                val crt = pk8.toPk1().toV1Cert(info2.getProperty("csr.info"))
                OpenSslHelper.Pfx(
                    name = info2.getProperty("pfx.alias") ?: "androiddebugkey",
                    thePassword = info2.getProperty("pfx.password") ?: "somepassword"
                )
                    .generate(pk8.toPk1(), crt)
                    .toJks()
                    .writeTo(outFile)
                log.info("$hint: $kFile => $outFile")
            }
            "toPk1" -> {
                val inBytes = File(kFile).readBytes()
                val k = CryptoHelper.KeyBox.parse2(inBytes) as Array<*>
                val kType = if ((k[1] as String) == "PEM") KeyFormat.PEM else KeyFormat.DER
                val outFile = File(info2.getProperty("file")).name + ".pk1"
                assert(k[2] is java.security.interfaces.RSAPrivateKey) {
                    "${k[2]!!::class} is NOT java.security.interfaces.RSAPrivateKey"
                }
                val hint = "RSA private: PK8($kType) => PK1(PEM)"
                log.info("Running: $hint")
                OpenSslHelper.PK8RsaKey(format = kType, data = File(kFile).readBytes())
                    .toPk1()
                    .also {
                        log.info("$kFile -> $outFile")
                    }
                    .writeTo(outFile)
            }
            "toPk8" -> {
                val inBytes = File(kFile).readBytes()
                val k = CryptoHelper.KeyBox.parse2(inBytes) as Array<*>
                val kType = if ((k[1] as String) == "PEM") KeyFormat.PEM else KeyFormat.DER
                val outFileStem = File(info2.getProperty("file")).name
                assert(k[2] is org.bouncycastle.asn1.pkcs.RSAPrivateKey)
                val hint = "RSA private: PK1 => PK8(PEM,DER)"
                log.info("Running: $hint")
                OpenSslHelper.PK1Key(data = File(kFile).readBytes()).let { rsa ->
                    rsa.toPk8(KeyFormat.PEM).writeTo("$outFileStem.pem_pk8")
                    rsa.toPk8(KeyFormat.DER).writeTo("$outFileStem.der_pk8")
                }
                log.info("$hint: $kFile => $outFileStem.pem_pk8, $outFileStem.der_pk8")
            }
            else -> {
                //pass
            }
        } //end-of-cmd-mode
    }

    fun processFile(inFile: String, info2: Properties) {
        val preFile = info2.getProperty("file")
        info2["file"] = (if (preFile == null) "" else "$preFile,") + File(inFile).canonicalFile.path
        info2["propFile"] = File(info2.getProperty("file")).name + ".prop"
        val k = CryptoHelper.KeyBox.parse2(File(inFile).readBytes()) as Array<*>
        val bSuccess = k[0] as Boolean
        val bType = k[1] as String
        if (bSuccess) {
            info2["fileType"] = bType
            info2["fileClazz"] = k[2]!!::class.toString()
            log.info("Recognized $bType: " + k[2]!!::class)
            if (k[2] is sun.security.x509.X509CertImpl) {
                val crt = k[2] as sun.security.x509.X509CertImpl
                val crtSubj = (crt.get("x509.info") as sun.security.x509.X509CertInfo).get("subject").toString()
                val subj2 = crtSubj
                    .replace(", ", "/")
                    .replace("EMAILADDRESS", "emailAddress")
                    .replace("\\s".toRegex(), "")
                info2.setProperty("csr.info", "/$subj2")
            }
            if (k[2] is sun.security.rsa.RSAPrivateCrtKeyImpl) {
                val pk8 = k[2] as sun.security.rsa.RSAPrivateCrtKeyImpl
                info2.setProperty("rsa.len", pk8.modulus.bitLength().toString())
                info2["rsa.modulus"] = Helper.toHexString(pk8.modulus.toByteArray())
                info2["rsa.privateExponent"] = Helper.toHexString(pk8.privateExponent.toByteArray())
                info2["rsa.publicExponent"] = Helper.toHexString(pk8.publicExponent.toByteArray())
                info2["rsa.primeP"] = Helper.toHexString(pk8.primeP.toByteArray())
                info2["rsa.primeQ"] = Helper.toHexString(pk8.primeQ.toByteArray())
            }
            if (k[2] is org.bouncycastle.asn1.pkcs.RSAPrivateKey) {
                val rsa = k[2] as org.bouncycastle.asn1.pkcs.RSAPrivateKey
                info2.setProperty("rsa.len", rsa.modulus.bitLength().toString())
                info2["rsa.modulus"] = Helper.toHexString(rsa.modulus.toByteArray())
                info2["rsa.privateExponent"] = Helper.toHexString(rsa.privateExponent.toByteArray())
                info2["rsa.publicExponent"] = Helper.toHexString(rsa.publicExponent.toByteArray())
                info2["rsa.primeP"] = Helper.toHexString(rsa.prime1.toByteArray())
                info2["rsa.primeQ"] = Helper.toHexString(rsa.prime2.toByteArray())
            }
        } else {
            if (bType == "NA") {
                log.warn("Unrecognized file $inFile")
            } else {
                log.warn("Recognized but not parsed $bType: $inFile")
            }
        }
    }

    fun todo(args: Array<String>) {
        when (args[0]) {
            "crypo.list" -> {
                CryptoHelper.listAll()
            }
            "crypto.key.5" -> {
                val hint = "RSA private(PK8): => Public Key(PK8, PEM)"
                val kFile = args[1]
                val outFile = args[2]
                assert((CryptoHelper.KeyBox.parse2(File(kFile).readBytes()) as Array<*>)[2] is org.bouncycastle.asn1.pkcs.RSAPrivateKey)
                val pk8rsa = OpenSslHelper.PK8RsaKey(KeyFormat.PEM, File(kFile).readBytes())
                pk8rsa.getPublicKey().writeTo(outFile)
                log.info("$hint: $kFile => $outFile")
            }
            "crypto.key.10" -> {
                //Action-10:
                var hint = "PK8 RSA: PEM <=> DER"
                val kFile = args[1]
                val outFile = args[2]
                val inBytes = File(kFile).readBytes()
                assert((CryptoHelper.KeyBox.parse2(inBytes) as Array<*>)[2] is java.security.interfaces.RSAPrivateKey)
                val p = PemReader(InputStreamReader(ByteArrayInputStream(File(kFile).readBytes()))).readPemObject()
                if (p != null) {//pem
                    hint = "PK8 RSA: PEM => DER"
                    OpenSslHelper.PK8RsaKey(KeyFormat.PEM, inBytes).transform(KeyFormat.PEM, KeyFormat.DER)
                        .writeTo(outFile)
                } else {//der
                    hint = "PK8 RSA: DER => PEM"
                    OpenSslHelper.PK8RsaKey(KeyFormat.DER, inBytes).transform(KeyFormat.DER, KeyFormat.PEM)
                        .writeTo(outFile)
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
            "crypto.key.22" -> {
                //Action-xx:
                val hint = "CRT ==> JKS"
                val kFile = args[1]
                val crtFile = args[2]
                val outFile = args[3]
                assert((CryptoHelper.KeyBox.parse2(File(crtFile).readBytes()) as Array<*>)[2] is Certificate)
                val envPassword = System.getProperty("password") ?: "secretpassword"
                val envAlias = System.getProperty("alias") ?: "someUnknownAlias"
                val crt = OpenSslHelper.Crt(File(crtFile).readBytes())
                val rsa = OpenSslHelper.PK1Key(KeyFormat.PEM, File(kFile).readBytes())
                OpenSslHelper.Pfx(name = envAlias, thePassword = envPassword).generate(rsa, crt).toJks()
                    .writeTo(outFile)
                log.info("$hint: $kFile => $outFile")
            }
            "crypto.key.jks" -> {
                //Action-xx:
                val hint = "Generate JKS"
                val keypass = args[1]
                val storepass = args[2]
                val alias = args[3]
                val outFile = args[4]
                OpenSslHelper.Jks.generate(keypass, storepass, alias, null, outFile)
                log.info("$hint: ==> $outFile")
            }
            "crypto.key.23" -> {
                //Action-xx:
                val hint = "PK1 ==> JKS"
                val kFile = args[1]
                val outFile = args[2]
                val rsa = OpenSslHelper.PK1Key(KeyFormat.PEM, File(kFile).readBytes())
                val crt = rsa.toV1Cert()
                OpenSslHelper.Pfx(name = "androiddebugkey", thePassword = "somepassword").generate(rsa, crt).toJks()
                    .writeTo(outFile)
                log.info("$hint: $kFile => $outFile")
            }
            "crypto.key.xx" -> {
                //Action-xx:
                val hint = ""
                val kFile = args[1]
                val k = CryptoHelper.KeyBox.parse2(File(kFile).readBytes()) as Array<*>
                println(k[2]!!.toString())
                val crt = k[2] as sun.security.x509.X509CertImpl
                log.info("type=" + crt.type + ", name=" + crt.name)
                for (item in crt.elements) {
                    log.info("# $item")
                }
                val crtInfo = crt.get("x509.info") as sun.security.x509.X509CertInfo
                for (item in crtInfo.elements) {
                    log.info("## $item")
                }
                log.info("subject=>")
                log.info(crtInfo.get("subject").toString())
                log.info("issuer=>")
                log.info(crtInfo.get("issuer").toString())
                log.info("$hint: $kFile => ")
            }
            else -> {
                Launcher.help()
                exitProcess(1)
            }
        }
    }
}


fun main(args: Array<String>) {
    val log = LoggerFactory.getLogger("main")
    val allSupportedCommands =
        listOf("genrsa", "toPublicKey", "decodePEM", "toPk8", "toPk1", "toJks", "toCsr", "toCrt").toString()

    val info2 = Properties().apply {
        this.setProperty("all_cmds", allSupportedCommands)
        this["available_cmds"] = this.getProperty("all_cmds")
        this["cmd"] = ""
        this.setProperty(
            "csr.info", "/C=CN/ST=Shanghai/L=Shanghai/O=XXX/OU=infra/CN=gerrit/emailAddress=webmaster@XX.com"
        )
        this.setProperty("pfx.alias", "androiddebugkey")
        this.setProperty("pfx.password", "somepassword")
    }

    if (args.isEmpty()) {
        info2.setProperty("rsa.len", 4096.toString())
        info2["file"] = "k"
        info2["propFile"] = "run.prop"
        FileOutputStream(info2.getProperty("propFile")).use { fos ->
            info2.store(fos, null)
            log.info("Writing to " + info2.getProperty("propFile"))
        }
        exitProcess(0)
    }

    if (args.size == 1 && File(args[0]).exists() && args[0].endsWith(".prop")) { //cmd mode
        Launcher().processCommand(args[0], info2)
    } else { // file mode
        args.forEachIndexed { index, s ->
            log.warn("[${index + 1}/${args.size}] Processing $s ...")
            Launcher().processFile(s, info2)
        }
        FileOutputStream(info2.getProperty("propFile")).use { fos ->
            info2.setProperty("all_cmds", allSupportedCommands)
            info2.store(fos, null)
            log.info("Writing to " + info2.getProperty("propFile"))
        }
    }
}