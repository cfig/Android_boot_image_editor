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

package avb

import avb.blob.AuthBlob
import avb.blob.AuxBlob
import avb.blob.Footer
import avb.blob.Header

/*
    a wonderfaul base64 encoder/decoder: https://cryptii.com/base64-to-hex
 */
@OptIn(ExperimentalUnsignedTypes::class)
class AVBInfo(var header: Header? = null,
              var authBlob: AuthBlob? = null,
              var auxBlob: AuxBlob? = null,
              var footer: Footer? = null)
