// Copyright 2021 yuyezhong@gmail.com
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

import avb.desc.UnknownDescriptor
import avb.desc.HashDescriptor
import cfig.Avb
import cfig.helper.CryptoHelper
import cfig.helper.Dumpling
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.commons.codec.binary.Hex
import org.junit.Test
import org.junit.Assert.*
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.File

class AvbTest {
    private val log = LoggerFactory.getLogger(AvbTest::class.java)

    @Test
    fun readDescriptors() {
        //output by "xxd -p <file>"
        val descStr = "000000000000000200000000000000b800000000017b9000736861323536" +
                "000000000000000000000000000000000000000000000000000000000004" +
                "000000200000002000000000000000000000000000000000000000000000" +
                "000000000000000000000000000000000000000000000000000000000000" +
                "000000000000000000000000626f6f7428f6d60b554d9532bd45874ab0cd" +
                "cb2219c4f437c9350f484fa189a881878ab6156408cd763ff119635ec9db" +
                "2a9656e220fa1dc27e26e59bd3d85025b412ffc3"
        val desc = UnknownDescriptor(ByteArrayInputStream(Hex.decodeHex(descStr)))
        val hashdDesc = HashDescriptor(ByteArrayInputStream(Hex.decodeHex(descStr)))
        log.info(desc.toString())
        log.info(hashdDesc.toString())
        val descAnalyzed = desc.analyze()
        assertTrue(descAnalyzed is HashDescriptor)
    }

    @Test
    fun verifyKnownKeysJson() {
        val keys = ObjectMapper().readValue(
            this::class.java.classLoader.getResource("known_keys.json"),
            object : TypeReference<List<Avb.Companion.KnownPublicKey>>() {})
        keys.forEachIndexed { _, knownPublicKey ->
            verifySha1(knownPublicKey)
        }
        val outStr = ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(keys)
        log.info(outStr)
    }

    private fun verifySha1(known: Avb.Companion.KnownPublicKey) {
        val hash = CryptoHelper.Hasher.hash(Dumpling(known.pubk), "sha-1")
        if (known.sha1.isBlank()) {
            log.info("+ add new sha1")
            known.sha1 = Hex.encodeHexString(hash)
        } else {
            if (known.sha1 == Hex.encodeHexString(hash)) {
                log.info("= sha1 match")
            } else {
                log.error("- sha1 mismatch")
                throw IllegalArgumentException("sha1 mismatch")
            }
        }
    }
}
