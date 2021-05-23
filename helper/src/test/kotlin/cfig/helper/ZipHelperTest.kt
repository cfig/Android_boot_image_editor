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

import cfig.helper.ZipHelper.Companion.dumpEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

class ZipHelperTest {

    @Before
    fun setUp() {
        File("out").let {
            if (!it.exists()) it.mkdir()
        }
    }

    @After
    fun tearDown() {
        File("out").deleteRecursively()
    }

    @Test
    fun unzip() {
        val zf = ZipHelperTest::class.java.classLoader.getResource("appcompat.zip").file
        ZipHelper.unzip(File(zf).path, "out")
        File("out").deleteRecursively()
    }

    @Test
    fun dumpEntry() {
        val zf = ZipHelperTest::class.java.classLoader.getResource("appcompat.zip").file
        ZipFile(zf).use {
           it.dumpEntry("webview.log", File("out/webview.log"))
        }
    }
}