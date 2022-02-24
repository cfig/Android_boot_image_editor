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

package cfig.helper

import org.bouncycastle.asn1.est.CsrAttrs
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.crmf.CertificateRequestMessageBuilder
import org.junit.Test
import org.slf4j.LoggerFactory
import java.io.File

class OpenSslHelperTest {
    private val log = LoggerFactory.getLogger(OpenSslHelperTest::class.java)

    @Test
    fun testCsr() {
        OpenSslHelper.createCsr(
            "test.key",
            "test.csr",
            "/C=US/ST=Utah/L=Lehi/O=Your Company, Inc./OU=IT/CN=yourdomain.com"
        )
    }

    @Test
    fun parsePk8() {
        val pk8 = CryptoHelperTest::class.java.classLoader.getResource("platform.pk8").file
        val x509 = CryptoHelperTest::class.java.classLoader.getResource("platform.x509.pem").file
        val kb = CryptoHelper.KeyBox.parse4(File(pk8).readBytes())
        println(kb.key::class)
        peekKeyBox(kb)
        OpenSslHelper.toJks(pk8, x509, "platform.jks")
    }

    private fun peekKeyBox(kb: CryptoHelper.KeyBox) {
        if (kb.fmt == CryptoHelper.KeyFormat.PEM) {
            println("fmt=[PEM] type=[" + kb.clazz.qualifiedName + "]")
        } else {
            println("type=[" + kb.clazz.qualifiedName + "]")
        }
    }

}
