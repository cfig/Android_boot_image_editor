package cfig.bootimg.v3

import cfig.io.Struct3
import org.slf4j.LoggerFactory
import java.io.InputStream

@OptIn(ExperimentalUnsignedTypes::class)
class VendorBootHeader(
        var headerVersion: UInt = 0U,
        var pageSize: UInt = 0U,
        var kernelLoadAddr: UInt = 0U,
        var ramdiskLoadAddr: UInt = 0U,
        var vndRamdiskSize: UInt = 0U,
        var cmdline: String = "",
        var tagsLoadAddr: UInt = 0U,
        var product: String = "",
        var headerSize: UInt = 0U,
        var dtbSize: UInt = 0U,
        var dtbLoadAddr: ULong = 0U
) {
    @Throws(IllegalArgumentException::class)
    constructor(iS: InputStream?) : this() {
        if (iS == null) {
            return
        }
        log.warn("VendorBootHeader constructor")
        val info = Struct3(FORMAT_STRING).unpack(iS)
        assert(12 == info.size)
        if (info[0] != magic) {
            throw IllegalArgumentException("stream doesn't look like Android Vendor Boot Image")
        }
        this.headerVersion = info[1] as UInt
        this.pageSize = info[2] as UInt
        this.kernelLoadAddr = info[3] as UInt
        this.ramdiskLoadAddr = info[4] as UInt
        this.vndRamdiskSize = info[5] as UInt
        this.cmdline = info[6] as String
        this.tagsLoadAddr = info[7] as UInt
        this.product = info[8] as String
        this.headerSize = info[9] as UInt
        this.dtbSize = info[10] as UInt
        this.dtbLoadAddr = info[11] as ULong

        assert(this.headerSize in arrayOf(VENDOR_BOOT_IMAGE_HEADER_V3_SIZE))
        assert(this.headerVersion == 3U)
    }

    fun encode(): ByteArray {
        return Struct3(FORMAT_STRING).pack(
                magic,
                headerVersion,
                pageSize,
                kernelLoadAddr,
                ramdiskLoadAddr,
                vndRamdiskSize,
                cmdline,
                tagsLoadAddr,
                product,
                headerSize,
                dtbSize,
                dtbLoadAddr)
    }

    companion object {
        private val log = LoggerFactory.getLogger(VendorBootHeader::class.java)
        const val magic = "VNDRBOOT"
        const val VENDOR_BOOT_IMAGE_HEADER_V3_SIZE = 2112U
        const val FORMAT_STRING = "8s" + //magic
                "I" + //header version
                "I" + //page size
                "I" + //kernel physical load addr
                "I" + //ramdisk physical load addr
                "I" + //vendor ramdisk size
                "2048s" + //cmdline
                "I" + //kernel tag load addr
                "16s" + //product name
                "I" + //header size
                "I" + //dtb size
                "Q" //dtb physical load addr
        init {
            assert(Struct3(FORMAT_STRING).calcSize().toUInt() == VENDOR_BOOT_IMAGE_HEADER_V3_SIZE)
        }
    }

    override fun toString(): String {
        return "VendorBootHeader(headerVersion=$headerVersion, pageSize=$pageSize, kernelLoadAddr=$kernelLoadAddr, ramdiskLoadAddr=$ramdiskLoadAddr, vndRamdiskSize=$vndRamdiskSize, cmdline='$cmdline', tagsLoadAddr=$tagsLoadAddr, product='$product', headerSize=$headerSize, dtbSize=$dtbSize, dtbLoadAddr=$dtbLoadAddr)"
    }
}
