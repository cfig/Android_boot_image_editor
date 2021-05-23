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

package cfig.bootimg.cpio

import cfig.io.Struct3
import org.apache.commons.compress.archivers.cpio.CpioConstants

/*
    cpio "New ASCII Format" with 070701 as magic
 */
class NewAsciiCpio(
    var c_magic: String = "070701", //start-of-header
    var c_ino: Long = 0,//var
    var c_mode: Long = 0,//var
    var c_uid: Int = 0,
    var c_gid: Int = 0,
    var c_nlink: Int = 1,
    var c_mtime: Long = 0,
    var c_filesize: Int = 0,//var
    var c_devmajor: Int = 0,
    var c_devminor: Int = 0,
    var c_rdevmajor: Int = 0,
    var c_rdevminor: Int = 0, //end-of-header
    var c_namesize: Int = 0, //c_string name with '\0', aka. name_len + 1
    var c_check: Int = 0
) {
    init {
        if (SIZE != Struct3(FORMAT_STRING).calcSize()) {
            throw RuntimeException()
        }
    }

    fun encode(): ByteArray {
        return Struct3(FORMAT_STRING).pack(
            String.format("%s", c_magic),
            String.format("%08x", c_ino),
            String.format("%08x", c_mode),
            String.format("%08x", c_uid),
            String.format("%08x", c_gid),
            String.format("%08x", c_nlink),
            String.format("%08x", c_mtime),
            String.format("%08x", c_filesize),
            String.format("%08x", c_devmajor),
            String.format("%08x", c_devminor),
            String.format("%08x", c_rdevmajor),
            String.format("%08x", c_rdevminor),
            String.format("%08x", c_namesize),
            String.format("%08x", c_check),
        )
    }

    private fun fileType(): Long {
        return c_mode and CpioConstants.S_IFMT.toLong()
    }

    fun isRegularFile(): Boolean {
        return fileType() == CpioConstants.C_ISREG.toLong()
    }

    fun isDirectory(): Boolean {
        return fileType() == CpioConstants.C_ISDIR.toLong()
    }

    fun isSymbolicLink(): Boolean {
        return fileType() == CpioConstants.C_ISLNK.toLong()
    }

    companion object {
        const val SIZE = 110
        const val FORMAT_STRING = "6s8s8s8s8s8s8s8s8s8s8s8s8s8s" //6 + 8 *13
    }
}

/*      <bits/stat.h>
    /* File types.  */
#define __S_IFDIR   0040000 /* Directory.  */
#define __S_IFCHR   0020000 /* Character device.  */
#define __S_IFBLK   0060000 /* Block device.  */
#define __S_IFREG   0100000 /* Regular file.  */
#define __S_IFIFO   0010000 /* FIFO.  */
#define __S_IFLNK   0120000 /* Symbolic link.  */
#define __S_IFSOCK  0140000 /* Socket.  */

    /* Protection bits.  */
#define __S_ISUID   04000   /* Set user ID on execution.  */
#define __S_ISGID   02000   /* Set group ID on execution.  */
#define __S_ISVTX   01000   /* Save swapped text after use (sticky).  */
#define __S_IREAD   0400    /* Read by owner.  */
#define __S_IWRITE  0200    /* Write by owner.  */
#define __S_IEXEC   0100    /* Execute by owner.  */
/* Read, write, and execute by owner.  */
#define S_IRWXU (__S_IREAD|__S_IWRITE|__S_IEXEC)

#define S_IRGRP (S_IRUSR >> 3)  /* Read by group.  */
#define S_IWGRP (S_IWUSR >> 3)  /* Write by group.  */
#define S_IXGRP (S_IXUSR >> 3)  /* Execute by group.  */
Read, write, and execute by group.
#define S_IRWXG (S_IRWXU >> 3)

#define S_IROTH (S_IRGRP >> 3)  /* Read by others.  */
#define S_IWOTH (S_IWGRP >> 3)  /* Write by others.  */
#define S_IXOTH (S_IXGRP >> 3)  /* Execute by others.  */
Read, write, and execute by others.
#define S_IRWXO (S_IRWXG >> 3)

# define ACCESSPERMS (S_IRWXU|S_IRWXG|S_IRWXO) 0777

 */
