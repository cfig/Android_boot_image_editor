// Copyright 2022-2023 yuyezhong@gmail.com
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

import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * Dumpling: Boxed type of data sources, which is the [filling] of the dumpling.
 * Constructor:
 *  [filling] can be of type String (file name) or ByteArray
 *  [label]   optional name of the Dumpling
 */
class Dumpling<in T>(private val filling: T, private val label: String? = null) {
    fun getLabel(): String {
        return label ?: getName()
    }

    fun getName(): String {
        return if (filling is String) "FILE:$filling" else "ByteArray"
    }

    fun getInputStream(): InputStream {
        return when (filling) {
            is String -> {
                FileInputStream(filling)
            }
            is ByteArray -> {
                ByteArrayInputStream(filling)
            }
            else -> {
                throw IllegalArgumentException("type ${filling!!::class} is not supported")
            }
        }
    }

    fun getLength(): Long {
        return when (filling) {
            is String -> {
                File(filling).length()
            }
            is ByteArray -> {
                filling.size.toLong()
            }
            else -> {
                throw IllegalArgumentException("type ${filling!!::class} is not supported")
            }
        }
    }

    @Throws(IllegalArgumentException::class)
    fun readFully(range: LongRange): ByteArray {
        when (filling) {
            is String -> {
                return ByteArray(range.count()).apply {
                    FileInputStream(filling).use { fis ->
                        fis.skip(range.first)
                        fis.read(this)
                    }
                }
            }
            is ByteArray -> {
                return filling.sliceArray(range.first.toInt()..range.last.toInt())
            }
            else -> {
                throw IllegalArgumentException("type ${filling!!::class} is not supported")
            }
        }
    }

    @Throws(IllegalArgumentException::class)
    fun readFully(loc: Pair<Long, Int>): ByteArray {
        when (filling) {
            is String -> {
                return ByteArray(loc.second).apply {
                    FileInputStream(filling).use { fis ->
                        if (loc.first < 0) {
                            fis.skip(getLength() + loc.first)
                        } else {
                            fis.skip(loc.first)
                        }
                        fis.read(this)
                    }
                }
            }
            is ByteArray -> {
                val subRangeStart = if (loc.first < 0) {
                    (getLength() + loc.first).toInt()
                } else {
                    loc.first.toInt()
                }
                return filling.sliceArray(subRangeStart until (subRangeStart + loc.second).toInt())
            }
            else -> {
                throw IllegalArgumentException("type ${filling!!::class} is not supported")
            }
        }
    }
}
