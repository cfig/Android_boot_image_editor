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

package avb.desc

import org.apache.commons.codec.binary.Hex
import org.junit.Assert
import org.junit.Test
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream

@OptIn(ExperimentalUnsignedTypes::class)
class HashDescriptorTest {
    private val log = LoggerFactory.getLogger(HashDescriptorTest::class.java)

    @Test
    fun parseHashDescriptor() {
        val descStr = "000000000000000200000000000000b80000000001ba4000736861323536000000000000000000000000000000000000000000000000000000000004000000200000002000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000626f6f74fbfb8e13c8082e0a16582163ad5075668903cc1237c6c007fed69de05957432103ae125531271eeeb83662cbe21543e3025f2d65268fb6b53c8718a90e3b03c7"
        val desc = HashDescriptor(ByteArrayInputStream(Hex.decodeHex(descStr)))
        log.info(desc.toString())
        Assert.assertEquals(descStr, Hex.encodeHexString(desc.encode()))
    }
}
