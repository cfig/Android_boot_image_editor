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

import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.slf4j.LoggerFactory

class AndroidHelper {
    companion object {
        private val log = LoggerFactory.getLogger(AndroidHelper::class.java)

        fun signFile(signer: String,
                     inFile: String,
                     outFile: String,
                     pemKey: String = "aosp/security/testkey.x509.pem",
                     pk8Key: String = "aosp/security/testkey.pk8") {
            var cmd = "java -Xmx2048m -jar $signer "
            cmd += " -w "
            cmd += " $pemKey "
            cmd += " $pk8Key "
            cmd += " $inFile "
            cmd += " $outFile "
            log.info("signFile: $cmd")
            DefaultExecutor().execute(CommandLine.parse(cmd))
        }
    }
}
