package cfig.bootimg

import groovy.json.JsonBuilder

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Created by yu at 10:58 on 2016-06-18
 */
class Parser {

    int readInt(InputStream is) {
        ByteBuffer bf = ByteBuffer.allocate(128);
        bf.order(ByteOrder.LITTLE_ENDIAN);
        byte[] data4 = new byte[4];
        assert 4 == is.read(data4)
        bf.clear();
        bf.put(data4);
        bf.flip();
        return bf.getInt();
    }

    byte[] readBytes(InputStream is, int len) {
        byte[] data4 = new byte[len];
        assert len == is.read(data4)
        return data4;
    }

    String unparse_os_version(int x) {
        int a = x >> 14;
        int b = (x - (a << 14)) >> 7;
        int c = x & 0x7f;

        return String.format("%d.%d.%d", a, b, c);
    }

    String unparse_os_patch_level(int x) {
        int y = x >> 4;
        int m = x & 0xf;
        y += 2000;

        return String.format("%d-%02d-%02d", y, m, 0);
    }

    int get_header_len(int pagesize) {
        int pad = (pagesize - (1632 & (pagesize - 1))) & (pagesize - 1);
        return pad + 1632;
    }

    int get_pad_len(int position, int pagesize) {
        return (pagesize - (position & (pagesize - 1))) & (pagesize - 1);
    }

    String bytes2String(byte[] inData) {
        StringBuilder sb = new StringBuilder("");
        for (int i = 0; i < inData.length; i++) {
            sb.append(Integer.toString((inData[i] & 0xff) + 0x100, 16).substring(1));
        }
        return sb.toString();
    }

    void parse_header(String fileName, CImgInfo inImgInfo) {
        InputStream is = new FileInputStream(new File(fileName))
        assert Arrays.equals(readBytes(is, 8), "ANDROID!".getBytes())
        inImgInfo.kernel_len = readInt(is);
        inImgInfo.kernel_offset = readInt(is);
        inImgInfo.ramdisk_len = readInt(is);
        inImgInfo.ramdisk_offset = readInt(is);
        inImgInfo.second_len = readInt(is);
        inImgInfo.second_offset = readInt(is);
        inImgInfo.tags_offset = readInt(is);
        inImgInfo.pagesize = readInt(is);
        assert 0 == readInt(is) //reserved
        int os_and_patch = readInt(is)
        if (0 != os_and_patch) { //treated as 'reserved' in this boot image
            inImgInfo.os_version = unparse_os_version(os_and_patch >> 11)
            inImgInfo.os_patch_level = unparse_os_patch_level(os_and_patch & 0x7ff)
        }
        inImgInfo.board = new String(readBytes(is, 16), "UTF-8").trim();
        if (0 == inImgInfo.board.length()) {
            inImgInfo.board = null;
        }
        inImgInfo.cmdline = new String(readBytes(is, 512), "UTF-8")
        inImgInfo.hash = readBytes(is, 32); //hash
        inImgInfo.cmdline += new String(readBytes(is, 1024), "UTF-8")
        inImgInfo.cmdline = inImgInfo.cmdline.trim();
        is.close();

        //calc subimg positions
        inImgInfo.kernel_pos = get_header_len(inImgInfo.pagesize)
        inImgInfo.ramdisk_pos = inImgInfo.kernel_pos + inImgInfo.kernel_len + get_pad_len(inImgInfo.kernel_len, inImgInfo.pagesize)
        inImgInfo.second_pos = inImgInfo.ramdisk_pos + inImgInfo.ramdisk_len + get_pad_len(inImgInfo.ramdisk_len, inImgInfo.pagesize)

        //adjust args
        if (inImgInfo.kernel_offset > 0x10000000) {
            inImgInfo.base = 0x10000000;
            inImgInfo.kernel_offset -= inImgInfo.base;
            inImgInfo.ramdisk_offset -= inImgInfo.base;
            inImgInfo.second_offset -= inImgInfo.base;
            inImgInfo.tags_offset -= inImgInfo.base;
        }
    }

    void extract_img_header(CImgInfo inImgInfo) {
        JsonBuilder jb = new JsonBuilder();
        String hashString = bytes2String(inImgInfo.hash);
        jb.bootimg {
            args {
//            kernel inImgInfo.kernel;
//            ramdisk inImgInfo.ramdisk;
//            second inImgInfo.second;
//            output inImgInfo.output;
                base "0x" + Integer.toHexString(inImgInfo.base);
                kernel_offset "0x" + Integer.toHexString(inImgInfo.kernel_offset);
                ramdisk_offset "0x" + Integer.toHexString(inImgInfo.ramdisk_offset);
                second_offset "0x" + Integer.toHexString(inImgInfo.second_offset);
                tags_offset "0x" + Integer.toHexString(inImgInfo.tags_offset);
                pagesize inImgInfo.pagesize;
                board inImgInfo.board;
                cmdline inImgInfo.cmdline;
                os_version inImgInfo.os_version;
                os_patch_level inImgInfo.os_patch_level;
                id inImgInfo.id;
            }
            img {
                kernel_pos inImgInfo.kernel_pos;
                kernel_len inImgInfo.kernel_len;
                ramdisk_pos inImgInfo.ramdisk_pos;
                ramdisk_len inImgInfo.ramdisk_len;
                second_pos inImgInfo.second_pos;
                second_len inImgInfo.second_len;
                hash hashString;
            }
        }
        FileWriter fw = new FileWriter(inImgInfo.cfg);
        fw.write(jb.toPrettyString());
        fw.flush();
        fw.close();
    }

    void extract_img_data(String inBootImg, String outImgName, int offset, int length) {
        if (0 == length) {
            return;
        }
        RandomAccessFile inRaf = new RandomAccessFile(inBootImg, "r");
        RandomAccessFile outRaf = new RandomAccessFile(outImgName, "rw");
        inRaf.seek(offset);
        byte[] data = new byte[length];
        assert length == inRaf.read(data)
        outRaf.write(data);
        outRaf.close();
        inRaf.close();
    }

    void printUsage() {
        println("Usage: abootimg <path_to_boot_image> [work_dir]");
        System.exit(1);
    }

    void abootimg(String[] arg) {
        CImgInfo imgInfo = new CImgInfo();
        String fileName;
        String workDir = "unzip_boot";
        if (1 == arg.length) {
            fileName = arg[0];
        } else if (2 == arg.length) {
            fileName = arg[0];
            workDir = arg[1];
        } else {
            printUsage();
        }
        imgInfo.kernel = workDir + File.separator + imgInfo.kernel;
        imgInfo.ramdisk = workDir + File.separator + imgInfo.ramdisk;
        imgInfo.second = workDir + File.separator + imgInfo.second;
        imgInfo.cfg = workDir + File.separator + imgInfo.cfg;

        parse_header(fileName, imgInfo);
        new File(workDir).mkdirs();
        extract_img_data(fileName, imgInfo.kernel, imgInfo.kernel_pos, imgInfo.kernel_len)
        extract_img_data(fileName, imgInfo.ramdisk, imgInfo.ramdisk_pos, imgInfo.ramdisk_len)
        extract_img_data(fileName, imgInfo.second, imgInfo.second_pos, imgInfo.second_len)
        extract_img_header(imgInfo);
    }
}
