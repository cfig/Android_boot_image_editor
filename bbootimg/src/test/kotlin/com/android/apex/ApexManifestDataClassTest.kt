package com.android.apex

import cfig.helper.CryptoHelper
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.Assert.*
import org.junit.Test
import org.slf4j.LoggerFactory

class ApexManifestDataClassTest {
    private val log = LoggerFactory.getLogger(ApexManifestDataClassTest::class.java)
    @Test
    fun parseAndPack() {
        val raw = ApexManifestDataClassTest::class.java.classLoader.getResourceAsStream("apex_manifest.pb")!!.readBytes()
        val originalHash = CryptoHelper.Hasher.sha256(raw)
        //parse
        val pb = ApexManifestOuterClass.ApexManifest.parseFrom(raw)
        log.info(pb.toString())
        val manifest = ApexManifestDataClass.fromOuterClass(pb)
        log.info(ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(manifest))
        //pack
        val convertedHash = CryptoHelper.Hasher.sha256(manifest!!.toPb().toByteArray())
        //check
        assertTrue(originalHash.contentEquals(convertedHash))
    }
}
