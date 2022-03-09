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

import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.slf4j.LoggerFactory
import java.io.File

class PayloadSigner {
    private val log = LoggerFactory.getLogger(PayloadSigner::class.java)
    var keySize = 0
    private val workDir = "build/staging_ota"
    val signingKey = "signing.key"
    val privKey = "aosp/security/testkey.pk8"
    val modulusFile = "$workDir/modulus.file"

    init {
        CommandLine.parse("openssl pkcs8 -in $privKey -inform DER -nocrypt -out $workDir/$signingKey").let { cmd ->
            log.info(cmd.toString())
            DefaultExecutor().execute(cmd)
        }

        CommandLine.parse("openssl rsa -inform PEM -in $workDir/$signingKey -modulus -noout -out $modulusFile").let { cmd ->
            log.info(cmd.toString())
            DefaultExecutor().execute(cmd)
        }

        val modulusString = File(modulusFile).readText()
        log.info(modulusString)
        val MODULUS_PREFIX = "Modulus="
        if (!modulusString.startsWith(MODULUS_PREFIX)) {
            throw IllegalArgumentException("Invalid modulus string")
        }
        keySize = modulusString.substring(MODULUS_PREFIX.length).length / 2
        log.info("key size = $keySize bytes")
        if (keySize !in listOf(256, 512)) {
            throw IllegalArgumentException("Unsupported key size")
        }
    }

    fun sign(inFile: String, outFile: String) {
        CommandLine.parse("openssl pkeyutl -sign").let { cmd ->
            cmd.addArguments("-inkey $workDir/$signingKey -pkeyopt digest:sha256")
            cmd.addArguments("-in $inFile")
            cmd.addArguments("-out $outFile")
            log.info(cmd.toString())
            DefaultExecutor().execute(cmd)
        }
    }
}
