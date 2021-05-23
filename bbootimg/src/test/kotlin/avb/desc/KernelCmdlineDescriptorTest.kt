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
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

@OptIn(ExperimentalUnsignedTypes::class)
class KernelCmdlineDescriptorTest {
    @Test
    fun encode() {
        val cmdStr1 = "000000000000000300000000000001a8000000010000019b646d3d22312076726f6f74206e6f6e6520726f20312c3020353135393939322076657269747920312050415254555549443d2428414e44524f49445f53595354454d5f5041525455554944292050415254555549443d2428414e44524f49445f53595354454d5f504152545555494429203430393620343039362036343439393920363434393939207368613120303963326230616435383532666330663461326430336566396432626535333732653262643133392032386636643630623535346439353332626434353837346162306364636232323139633466343337633933353066343834666131383961383831383738616236203130202428414e44524f49445f5645524954595f4d4f4445292069676e6f72655f7a65726f5f626c6f636b73207573655f6665635f66726f6d5f6465766963652050415254555549443d2428414e44524f49445f53595354454d5f504152545555494429206665635f726f6f74732032206665635f626c6f636b7320363530303830206665635f7374617274203635303038302220726f6f743d2f6465762f646d2d300000000000"
        val cmdStr2 = "000000000000000300000000000000300000000200000028726f6f743d50415254555549443d2428414e44524f49445f53595354454d5f504152545555494429"
        val cmd1 = KernelCmdlineDescriptor(ByteArrayInputStream(Hex.decodeHex(cmdStr1)), 0)
        assertEquals(cmdStr1, Hex.encodeHexString(cmd1.encode()))

        val cmd2 = KernelCmdlineDescriptor(ByteArrayInputStream(Hex.decodeHex(cmdStr2)), 0)
        assertEquals(cmdStr2, Hex.encodeHexString(cmd2.encode()))
    }
}
