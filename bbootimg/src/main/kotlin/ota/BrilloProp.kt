// Copyright 2022 yuyezhong@gmail.com
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

package cc.cfig.droid.ota

import cfig.helper.ZipHelper.Companion.getEntryOffset
import org.apache.commons.compress.archivers.zip.ZipFile
import org.slf4j.LoggerFactory
import java.io.File

// tuple(name, offset, size) of an zip entry
class BrilloProp(
    var name: String,
    var offset: Long,
    var size: Long
) {
    constructor(zf: ZipFile, entryName: String) : this("", 0, 0) {
        val entry = zf.getEntry(entryName)
        name = File(entryName).name
        offset = entry.getEntryOffset()
        size = entry.size
        log.debug("extra size = " + entry.localFileDataExtra.size)
        log.debug("file name len = " + entry.name.length)
    }

    companion object {
        private val log = LoggerFactory.getLogger(BrilloProp::class.java)
    }

    override fun toString(): String {
        return if (offset == 0L && size == 0L) {
            name + " ".repeat(15)
        } else {
            "$name:$offset:$size"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as BrilloProp
        if (name != other.name) return false
        if (offset != other.offset) return false
        if (size != other.size) return false
        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + offset.hashCode()
        result = 31 * result + size.hashCode()
        return result
    }
}
