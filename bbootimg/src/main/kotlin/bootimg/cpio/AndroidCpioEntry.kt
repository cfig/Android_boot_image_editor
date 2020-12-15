package cfig.bootimg.cpio

import cfig.helper.Helper
import com.fasterxml.jackson.annotation.JsonIgnore
import org.apache.commons.compress.archivers.cpio.CpioArchiveEntry
import org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream
import org.apache.commons.compress.archivers.cpio.CpioConstants
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream

class AndroidCpioEntry(
    var name: String = "",
    var statMode: Long = 0,// stat.st_mode
    @JsonIgnore
    val data: ByteArray = byteArrayOf(),
    var ino: Long = 0,
    var note: String = ""
) {
    fun encode(): ByteArray {
        //new ascii cpio
        var header = Helper.join(
            NewAsciiCpio(
                c_ino = ino,
                c_mode = statMode,
                c_filesize = data.size,
                c_namesize = name.length + 1
            ).encode(),
            name.toByteArray(),
            ByteArray(1)
        ) //NULL terminated c-string
        //padding header if necessary
        Helper.round_to_multiple(header.size, 4).let { roundedSize ->
            if (roundedSize != header.size) {
                log.debug("header: meta ${header.size - 1 - name.length}, name ${name.length}, null 1, pad ${roundedSize - header.size} -> $roundedSize")
                header = Helper.join(header, ByteArray(roundedSize - header.size))
            }
        }
        var payload = data
        //padding data if necessary
        Helper.round_to_multiple(payload.size, 4).let { roundedSize ->
            if (roundedSize != payload.size) {
                log.debug("data  : payload ${payload.size}, pad ${roundedSize - payload.size} -> $roundedSize")
                payload = Helper.join(payload, ByteArray(roundedSize - payload.size))
            }
        }
        log.debug("entry($name): header ${header.size} + data ${payload.size} = ${header.size + payload.size}")
        return Helper.join(header, payload)
    }

    fun encode2(): ByteArray {
        val baos = ByteArrayOutputStream()
        val cpio = CpioArchiveOutputStream(baos)
        val entry = CpioArchiveEntry(CpioConstants.FORMAT_NEW, name).apply {
            inode = ino
            uid = 0
            gid = 0
            mode = statMode
            numberOfLinks = 1
            time = 0
            size = data.size.toLong()
            deviceMaj = 0
            deviceMin = 0
            remoteDeviceMaj = 0
            remoteDeviceMin = 0
            chksum = 0
        }
        cpio.putArchiveEntry(entry)
        cpio.write(data)
        cpio.closeArchiveEntry()
        return baos.toByteArray()
    }

    data class FileMode(
        var type: String = "",
        var sbits: String = "", //suid, sgid, sticky
        var perm: String = ""
    )

    companion object {
        private val log = LoggerFactory.getLogger(AndroidCpioEntry::class.java)
        private val S_IFDIR = java.lang.Long.valueOf("040000", 8)
        private val S_IFCHR = java.lang.Long.valueOf("020000", 8)
        private val S_IFBLK = java.lang.Long.valueOf("060000", 8)
        private val S_IFREG = java.lang.Long.valueOf("100000", 8)
        private val S_IFIFO = java.lang.Long.valueOf("010000", 8)
        private val S_IFLNK = java.lang.Long.valueOf("120000", 8)
        private val S_IFSOCK = java.lang.Long.valueOf("140000", 8)

        private val S_ISUID = java.lang.Long.valueOf("4000", 8)
        private val S_ISGID = java.lang.Long.valueOf("2000", 8)
        private val S_ISVTX = java.lang.Long.valueOf("1000", 8)

        private val S_IREAD = java.lang.Long.valueOf("400", 8)
        private val S_IWRITE = java.lang.Long.valueOf("200", 8)
        private val S_IEXEC = java.lang.Long.valueOf("100", 8)

        private val MASK_S_IRWXU = S_IREAD or S_IWRITE or S_IEXEC
        private val MASK_S_IRWXG = MASK_S_IRWXU shr 3
        private val MASK_S_IRWXO = MASK_S_IRWXG shr 3
        private val MASK_ACCESSPERMS = MASK_S_IRWXU or MASK_S_IRWXG or MASK_S_IRWXO

        fun interpretMode(mode: Long): FileMode {
            return FileMode().let { fm ->
                val m = mode and java.lang.Long.valueOf("0170000", 8) // S_IFMT
                fm.type = when (m) {
                    S_IFREG -> "REG"
                    S_IFCHR -> "CHR"
                    S_IFBLK -> "BLK"
                    S_IFDIR -> "DIR"
                    S_IFIFO -> "FIFO"
                    S_IFLNK -> "LNK"
                    S_IFSOCK -> "SOCK"
                    else -> throw IllegalArgumentException("unknown file type " + java.lang.Long.toOctalString(m))
                }
                if ((mode and S_ISUID) != 0L) {
                    fm.sbits += if (fm.sbits.isEmpty()) "suid" else "|suid"
                }
                if ((mode and S_ISGID) != 0L) {
                    fm.sbits += if (fm.sbits.isEmpty()) "sgid" else "|sgid"
                }
                if ((mode and S_ISVTX) != 0L) {
                    fm.sbits += if (fm.sbits.isEmpty()) "sticky" else "|sticky"
                }
                fm.perm = java.lang.Long.toOctalString(mode and MASK_ACCESSPERMS)
                fm
            }
        }
    }
}