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

package avb.alg

import cfig.io.Struct3

class Algorithms {
    @OptIn(ExperimentalUnsignedTypes::class)
    companion object {
        private val algMap = mutableMapOf<String, Algorithm>()
        fun get(name: String): Algorithm? {
            return algMap[name]
        }

        fun get(algorithm_type: Int): Algorithm? {
            for (item in algMap) {
                if (item.value.algorithm_type == algorithm_type) {
                    return item.value
                }
            }
            return null
        }

        init {
            val NONE = Algorithm(name = "NONE")

            val SHA256_RSA2048 = Algorithm(
                    algorithm_type = 1,
                    name = "SHA256_RSA2048",
                    hash_name = "sha256",
                    hash_num_bytes = 32,
                    signature_num_bytes = 256,
                    public_key_num_bytes = 8 + 2 * 2048 / 8,
                    padding = Struct3("2b202x1b19B").pack(
                            byteArrayOf(0x00, 0x01),
                            0xff,
                            byteArrayOf(0x00),
                            intArrayOf(0x30, 0x31, 0x30, 0x0d, 0x06, 0x09, 0x60, 0x86,
                                    0x48, 0x01, 0x65, 0x03, 0x04, 0x02, 0x01, 0x05,
                                    0x00, 0x04, 0x20)),
                    defaultKey = "aosp/avb/avb_test_data/testkey_rsa2048.pem")

            val SHA256_RSA4096 = Algorithm(
                    name = "SHA256_RSA4096",
                    algorithm_type = 2,
                    hash_name = "sha256",
                    hash_num_bytes = 32,
                    signature_num_bytes = 512,
                    public_key_num_bytes = 8 + 2 * 4096 / 8,
                    padding = Struct3("2b458x1x19B").pack(
                            byteArrayOf(0x00, 0x01),
                            0xff,
                            0x00,
                            intArrayOf(0x30, 0x31, 0x30, 0x0d, 0x06, 0x09, 0x60, 0x86,
                                    0x48, 0x01, 0x65, 0x03, 0x04, 0x02, 0x01, 0x05,
                                    0x00, 0x04, 0x20)
                    ),
                    defaultKey = "aosp/avb/avb_test_data/testkey_rsa4096.pem"
            )

            val SHA256_RSA8192 = Algorithm(
                    name = "SHA256_RSA8192",
                    algorithm_type = 3,
                    hash_name = "sha256",
                    hash_num_bytes = 32,
                    signature_num_bytes = 1024,
                    public_key_num_bytes = 8 + 2 * 8192 / 8,
                    padding = Struct3("2b970x1x19B").pack(
                            intArrayOf(0x00, 0x01),
                            0xff,
                            0x00,
                            intArrayOf(0x30, 0x31, 0x30, 0x0d, 0x06, 0x09, 0x60, 0x86,
                                    0x48, 0x01, 0x65, 0x03, 0x04, 0x02, 0x01, 0x05,
                                    0x00, 0x04, 0x20)),
                    defaultKey = "aosp/avb/avb_test_data/testkey_rsa8192.pem")

            val SHA512_RSA2048 = Algorithm(
                    name = "SHA512_RSA2048",
                    algorithm_type = 4,
                    hash_name = "sha512",
                    hash_num_bytes = 64,
                    signature_num_bytes = 256,
                    public_key_num_bytes = 8 + 2 * 2048 / 8,
                    padding = Struct3("2b170x1x19B").pack(
                            intArrayOf(0x00, 0x01),
                            0xff,
                            0x00,
                            intArrayOf(0x30, 0x51, 0x30, 0x0d, 0x06, 0x09, 0x60, 0x86,
                                    0x48, 0x01, 0x65, 0x03, 0x04, 0x02, 0x03, 0x05,
                                    0x00, 0x04, 0x40)),
                    defaultKey = "aosp/avb/avb_test_data/testkey_rsa2048.pem")

            val SHA512_RSA4096 = Algorithm(
                    name = "SHA512_RSA4096",
                    algorithm_type = 5,
                    hash_name = "sha512",
                    hash_num_bytes = 64,
                    signature_num_bytes = 512,
                    public_key_num_bytes = 8 + 2 * 4096 / 8,
                    padding = Struct3("2b426x1x19B").pack(
                            intArrayOf(0x00, 0x01),
                            0xff,
                            0x00,
                            intArrayOf(0x30, 0x51, 0x30, 0x0d, 0x06, 0x09, 0x60, 0x86,
                                    0x48, 0x01, 0x65, 0x03, 0x04, 0x02, 0x03, 0x05,
                                    0x00, 0x04, 0x40)),
                    defaultKey = "aosp/avb/avb_test_data/testkey_rsa4096.pem")

            val SHA512_RSA8192 = Algorithm(
                    name = "SHA512_RSA8192",
                    algorithm_type = 6,
                    hash_name = "sha512",
                    hash_num_bytes = 64,
                    signature_num_bytes = 1024,
                    public_key_num_bytes = 8 + 2 * 8192 / 8,

                    padding = Struct3("2b938x1x19B").pack(
                            intArrayOf(0x00, 0x01),
                            0xff,
                            0x00,
                            intArrayOf(0x30, 0x51, 0x30, 0x0d, 0x06, 0x09, 0x60, 0x86,
                                    0x48, 0x01, 0x65, 0x03, 0x04, 0x02, 0x03, 0x05,
                                    0x00, 0x04, 0x40)),
                    defaultKey = "aosp/avb/avb_test_data/testkey_rsa8192.pem")

            algMap[NONE.name] = NONE

            algMap[SHA256_RSA2048.name] = SHA256_RSA2048
            algMap[SHA256_RSA4096.name] = SHA256_RSA4096
            algMap[SHA256_RSA8192.name] = SHA256_RSA8192

            algMap[SHA512_RSA2048.name] = SHA512_RSA2048
            algMap[SHA512_RSA4096.name] = SHA512_RSA4096
            algMap[SHA512_RSA8192.name] = SHA512_RSA8192
        }
    }
}
