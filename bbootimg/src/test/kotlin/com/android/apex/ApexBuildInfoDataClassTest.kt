package com.android.apex

import cfig.helper.CryptoHelper
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.Test
import org.slf4j.LoggerFactory
import kotlin.test.assertTrue

class ApexBuildInfoDataClassTest {
    private val log = LoggerFactory.getLogger(ApexBuildInfoOuterClass::class.java)
    @Test
    fun parseAndPack() {
        val raw = ApexBuildInfoDataClassTest::class.java.classLoader.getResourceAsStream("apex_build_info.pb")!!.readBytes()
        val originalHash = CryptoHelper.Hasher.sha256(raw)
        //parse
        val abiPb = ApexBuildInfoOuterClass.ApexBuildInfo.parseFrom(raw)
        log.info(abiPb.toString())
        val abiJava = ApexBuildInfoDataClass.fromOuterClass(abiPb)
        log.info(ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(abiJava))
        //pack
        val convertedHash = CryptoHelper.Hasher.sha256(abiJava!!.toPb().toByteArray())
        //check
        assertTrue(originalHash.contentEquals(convertedHash))
    }
}
