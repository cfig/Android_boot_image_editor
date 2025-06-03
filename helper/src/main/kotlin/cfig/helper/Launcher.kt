// Copyright 2021-2023 yuyezhong@gmail.com
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

@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package cfig.helper

import cfig.helper.CryptoHelper.KeyBox.Companion.bcRSA2RSA
import cfig.helper.CryptoHelper.KeyBox.Companion.pk8toPk1
import cfig.helper.CryptoHelper.KeyBox.Companion.rsa2jwk
import com.fasterxml.jackson.databind.ObjectMapper
import org.bouncycastle.util.io.pem.PemObject
import org.bouncycastle.util.io.pem.PemWriter
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileWriter
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.cert.X509Certificate
import java.security.spec.RSAPublicKeySpec
import java.util.*
import kotlin.system.exitProcess

class Launcher {
    companion object {
        val allSupportedCommands =
            listOf("genrsa", "toPublicKey", "decodePEM", "toPk8", "toPk1", "toJks", "toCsr", "toCrt").toString()

        private val log = LoggerFactory.getLogger(Launcher::class.java)

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

    //https://stackoverflow.com/questions/7611383/generating-rsa-keys-in-pkcs1-format-in-java
    private fun dumpPem(pem: PemObject, outFile: String) {
        FileWriter(outFile).use { fw ->
            log.info("Writing ${pem.type}to $outFile")
            PemWriter(fw).use { pw ->
                pw.writeObject(pem)
            }
        }
    }

    private fun dumpPublicKey(n: BigInteger, e: BigInteger, outFile: String) {
        val pubK = KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(n, e))
        FileWriter(outFile).use { fw ->
            log.info("Writing public key to $outFile")
            PemWriter(fw).use { pw ->
                pw.writeObject(PemObject("RSA PUBLIC KEY", pubK.encoded))
            }
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
                val keyPair = KeyPairGenerator.getInstance("RSA").let {
                    it.initialize(kLen)
                    it.generateKeyPair()
                }
                FileOutputStream(kFile + ".pk8", false).use {
                    it.write(keyPair.private.encoded)
                    log.info("RSA priv key(len=$kLen) written to $kFile.pk8")
                }

                dumpPem(PemObject("RSA PRIVATE KEY", pk8toPk1(keyPair.private).encoded), "$kFile.pem.pk1")

                FileOutputStream("$kFile.pub", false).use {
                    it.write(keyPair.public.encoded)
                    log.info("RSA pub  key(len=$kLen) written to $kFile.pub")
                }
                dumpPem(PemObject("RSA PUBLIC KEY", keyPair.public.encoded), "$kFile.pem.pub")
            }

            "toPub" -> {
                val k = CryptoHelper.KeyBox.parse4(File(kFile).readBytes())
                if (k.key is org.bouncycastle.asn1.pkcs.RSAPrivateKey) {
                    dumpPublicKey(k.key.modulus, k.key.publicExponent, "pub.k")
                } else if (k.key is sun.security.rsa.RSAPrivateCrtKeyImpl) {
                    dumpPublicKey(k.key.modulus, k.key.publicExponent, "pub.k")
                } else {
                    throw IllegalArgumentException("not supported")
                }
            }

            "parse" -> {
                val k = CryptoHelper.KeyBox.parse4(File(kFile).readBytes())
                log.info("fmt=" + k.fmt.toString())
                log.info("clazz=" + k.clazz)
                log.info("key=" + k.key)
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
        val unboxed = CryptoHelper.UnBoxed.fromData(File(inFile).readBytes())
        val k2 = unboxed.parse()
        var retJwk: com.nimbusds.jose.jwk.RSAKey? = null
        if (k2 is java.security.interfaces.RSAPrivateCrtKey) {
            retJwk = rsa2jwk(k2)
            //log.info(retJwk.toJSONString())
        } else if (k2 is org.bouncycastle.asn1.pkcs.RSAPrivateKey) {
            val privk = bcRSA2RSA(k2)
            check(privk is java.security.interfaces.RSAPrivateCrtKey)
            retJwk = rsa2jwk(privk)
            //log.info(retJwk.toJSONString())
        } else if (k2 is java.security.interfaces.RSAPublicKey) {
            retJwk = rsa2jwk(k2)
            //log.info(retJwk.toJSONString())
        } else if (k2 is X509Certificate) {
            retJwk = rsa2jwk(k2.publicKey as java.security.interfaces.RSAPublicKey)
        } else {
            log.info("Found other: " + k2!!::class.java.toString())
        }
        if (retJwk != null) {
            log.info("transforming: $inFile --> out.jwk ...")
            File("out.jwk").writeText(
                ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(
                    ObjectMapper().readValue(retJwk.toJSONString(), Any::class.java)
                )
            )
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
    }
}
