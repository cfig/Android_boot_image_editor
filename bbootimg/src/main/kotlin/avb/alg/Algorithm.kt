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

data class Algorithm(
        val name: String = "NONE",
        val algorithm_type: Int = 0,
        val hash_name: String = "",
        val hash_num_bytes: Int = 0,
        val signature_num_bytes: Int = 0,
        val public_key_num_bytes: Int = 0,
        val padding: ByteArray = byteArrayOf(),
        val defaultKey: String ="")