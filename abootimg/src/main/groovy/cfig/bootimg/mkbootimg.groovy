package cfig.bootimg

// http://mvnrepository.com/artifact/net.sf.jopt-simple/jopt-simple
//@Grapes(
//    @Grab(group='net.sf.jopt-simple', module='jopt-simple', version='5.0.1')
//)
import java.nio.ByteBuffer;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import java.nio.channels.FileChannel;
import java.nio.ByteOrder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.security.MessageDigest;

CArgs parse_cmdline(String[] inArgs) {
    OptionParser parser = new OptionParser();
    parser.accepts("kernel", "path to the kernel").withRequiredArg();
    parser.accepts("ramdisk", "path to the ramdisk").withRequiredArg();
    parser.accepts("second", "path to the 2nd bootloader").withRequiredArg();
    parser.accepts("cmdline", "extra arguments to be passed on the kernel command line").withRequiredArg();
    parser.accepts("base", "base address").withRequiredArg();
    parser.accepts("kernel_offset", "kernel offset").withRequiredArg();
    parser.accepts("ramdisk_offset", "ramdisk offset").withRequiredArg();
    parser.accepts("second_offset", "2nd bootloader offset").withRequiredArg();
    parser.accepts("os_version", "operating system version").withRequiredArg();
    parser.accepts("os_patch_level", "operating system patch level").withRequiredArg();
    parser.accepts("tags_offset", "tags offset").withRequiredArg();
    parser.accepts("board", "board name").withRequiredArg();
    parser.accepts("pagesize", "page size").withRequiredArg();
    parser.accepts("id", "print the image ID on standard output");
    parser.accepts("output", "output file name").withRequiredArg();

    OptionSet options = parser.parse(inArgs)
    CArgs ret = new CArgs();

    ret.kernel = options.valueOf("kernel")

    ret.output = options.valueOf("output")

    ret.ramdisk = options.valueOf("ramdisk")

    ret.second = options.valueOf("second")

    if (options.has("board")) {
        ret.board = options.valueOf("board")
    } else {
        ret.board = ""
    }

    ret.id = options.has("id")

    if (options.has("base")) {
        ret.base = Integer.decode(options.valueOf("base"))
    } else {
        ret.base = 0x10000000;
    }

    if (options.has("kernel_offset")) {
        ret.kernel_offset = Integer.decode(options.valueOf("kernel_offset"))
    } else {
        ret.kernel_offset = 0x00008000;
    }

    if (options.has("ramdisk_offset")) {
        ret.ramdisk_offset = Integer.decode(options.valueOf("ramdisk_offset"))
    } else {
        ret.ramdisk_offset = 0x01000000
    }

    ret.os_version = options.valueOf("os_version")

    ret.os_patch_level = options.valueOf("os_patch_level")

    if (options.has("second_offset")) {
        ret.second_offset = Integer.decode(options.valueOf("second_offset"))
    } else {
        ret.second_offset = 0x00f00000
    }

    if (options.has("tags_offset")) {
        ret.tags_offset = Integer.decode(options.valueOf("tags_offset"))
    } else {
        ret.tags_offset = 0x00000100
    }

    if (options.has("pagesize")) {
        ret.pagesize = Integer.decode(options.valueOf("pagesize"))
    } else {
        ret.pagesize = 2048
    }

    if (options.has("cmdline")) {
        ret.cmdline = options.valueOf("cmdline")
    } else {
        ret.cmdline = ""
    }

    if (ret.cmdline.length() > 1536) {
        println("cmdline length must <= 1536, current is " + ret.cmdline.length());
        printUsage(parser);
    }
    if (null == ret.kernel) {
        println("kernel must not be empty");
        printUsage(parser);
    }
    if (null == ret.output) {
        println("output file must not be empty");
        printUsage(parser);
    }
    if (ret.board.length() > 16) {
        println("board name length must <= 16")
        printUsage(parser);
    }

    return ret;
}

byte[] write_header(CArgs inArgs) {
    ByteBuffer bf = ByteBuffer.allocate(1024 * 32);
    bf.order(ByteOrder.LITTLE_ENDIAN);

    //header start
    bf.put("ANDROID!".getBytes())
    bf.putInt((int) new File(inArgs.kernel).length());
    bf.putInt(inArgs.base + inArgs.kernel_offset)

    if (null == inArgs.ramdisk) {
        bf.putInt(0)
    } else {
        bf.putInt((int) new File(inArgs.ramdisk).length());
    }

    bf.putInt(inArgs.base + inArgs.ramdisk_offset)

    if (null == inArgs.second) {
        bf.putInt(0)
    } else {
        bf.putInt((int) new File(inArgs.second).length());
    }

    bf.putInt(inArgs.base + inArgs.second_offset)
    bf.putInt(inArgs.base + inArgs.tags_offset)
    bf.putInt(inArgs.pagesize)
    bf.putInt(0);
    bf.putInt((parse_os_version(inArgs.os_version) << 11) | parse_os_patch_level(inArgs.os_patch_level))

    bf.put(inArgs.board.getBytes())
    bf.put(new byte[16 - inArgs.board.length()])
    bf.put(inArgs.cmdline.substring(0, Math.min(512, inArgs.cmdline.length())).getBytes())
    bf.put(new byte[512 - Math.min(512, inArgs.cmdline.length())])
    byte[] img_id = hashFile(inArgs.kernel, inArgs.ramdisk, inArgs.second)
    bf.put(img_id)
    bf.put(new byte[32 - img_id.length])

    if (inArgs.cmdline.length() > 512) {
        bf.put(inArgs.cmdline.substring(512).getBytes())
        bf.put(new byte[1024 + 512 - inArgs.cmdline.length()])
    } else {
        bf.put(new byte[1024])
    }

    //padding
    pad_file(bf, inArgs.pagesize)

    //write
    FileOutputStream fos = new FileOutputStream(inArgs.output, false);
    fos.write(bf.array(), 0, bf.position())
    fos.close();

    return img_id;
}

void printUsage(OptionParser p) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write("Usage: mkbootimg <option>\n".getBytes());
    p.printHelpOn(out);
    System.out.println(out.toString());
    out.close();
    System.exit(1);
}

void write_padded_file(ByteBuffer inBF, String srcFile, int padding) {
    if (null == srcFile) {
        return;
    }
    InputStream is = new FileInputStream(new File(srcFile))
    int byteRead;
    byte[] dataRead = new byte[128]
    while (true) {
        byteRead = is.read(dataRead)
        if (-1 == byteRead) {
            break;
        }
        inBF.put(dataRead, 0, byteRead);
    }
    is.close();
    pad_file(inBF, padding)
}

void pad_file(ByteBuffer inBF, int padding) {
    int pad = (padding - (inBF.position() & (padding - 1))) & (padding - 1);
    inBF.put(new byte[pad]);
}

void write_data(CArgs inArgs) {
    ByteBuffer bf = ByteBuffer.allocate(1024 * 1024 * 64);
    bf.order(ByteOrder.LITTLE_ENDIAN);

    write_padded_file(bf, inArgs.kernel, inArgs.pagesize)
    write_padded_file(bf, inArgs.ramdisk, inArgs.pagesize)
    write_padded_file(bf, inArgs.second, inArgs.pagesize)

    //write
    FileOutputStream fos = new FileOutputStream(inArgs.output, true);
    fos.write(bf.array(), 0, bf.position())
    fos.close();
}

int parse_os_patch_level(x) {
    if (null == x) {
        return 0;
    }
    int ret = 0
    Pattern pattern = Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})")
    Matcher matcher = pattern.matcher(x)
    if (matcher.find()) {
        int y = Integer.decode(matcher.group(1)) - 2000
        int m = Integer.decode(matcher.group(2))
        // 7 bits allocated for the year, 4 bits for the month
        assert y >= 0 && y < 128
        assert m > 0 && m <= 12
        ret = (y << 4) | m
    } else {
        throw new IllegalArgumentException("invalid os_patch_level")
    }

    return ret;
}

int parse_os_version(x) {
    int ret = 0;
    if (null != x) {
        Pattern pattern = Pattern.compile("^(\\d{1,3})(?:\\.(\\d{1,3})(?:\\.(\\d{1,3}))?)?");
        Matcher m = pattern.matcher(x)
        if (m.find()) {
            int a = Integer.decode(m.group(1))
            int b = 0;
            int c = 0;
            if (m.groupCount() >= 2) {
                b = Integer.decode(m.group(2))
            }
            if (m.groupCount() == 3) {
                c = Integer.decode(m.group(3))
            }
            assert a < 128
            assert b < 128
            assert c < 128
            ret = ((a << 14) | (b << 7) | c)
        } else {
            throw new IllegalArgumentException("invalid os_version")
        }
    }

    return ret;
}

void test() {
    ByteBuffer b2 = ByteBuffer.allocate(1024);
    b2.order(ByteOrder.LITTLE_ENDIAN);
    b2.putInt(Integer.MAX_VALUE); //4 bytes
    println("max: " + Integer.MAX_VALUE)
    println("min: " + Integer.MIN_VALUE)
    b2.putInt(0x11111111)
    b2.putInt(Integer.MIN_VALUE);
    b2.putInt(0x11111111)
    b2.put("welcome".getBytes())
    b2.put(new byte[5]);
    b2.putInt(0x11111111)
    b2.putInt(0);
    b2.putInt(0x11111111)
    //b2.put((byte)0);
    b2.flip();
    FileChannel fc2 = new FileOutputStream(new File("ftest"), false).getChannel();
    fc2.write(b2);
    fc2.close();

//ByteBuffer bf = ByteBuffer.allocate(1024 * 1024 * 50);
//bf.order(ByteOrder.LITTLE_ENDIAN);
//bf.flip()
//boolean append = false;
//FileChannel fc = new FileOutputStream(new File("f1"), append).getChannel();
//fc.write(bf);
//fc.close();
//
//FileOutputStream stream = new FileOutputStream("f2");
//stream.write(bf.array(), 0, bf.position())
//stream.close();
}

byte[] hashFile(String... inFiles) {
    MessageDigest md = MessageDigest.getInstance("SHA1")

    for (String item : inFiles) {
        ByteBuffer itemBF = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        if (null == item) {
            md.update(itemBF.putInt(0).array())
        } else {
            InputStream is = new FileInputStream(new File(item))
            int byteRead;
            byte[] dataRead = new byte[128]
            while (true) {
                byteRead = is.read(dataRead)
                if (-1 == byteRead) {
                    break;
                }
                md.update(dataRead, 0, byteRead)
            }
            is.close();
            md.update(itemBF.putInt((int) new File(item).length()).array())
        }
    }

    return md.digest();
}

void dumpBytes(byte[] inData) {
    StringBuffer sb = new StringBuffer("");
    for (int i = 0; i < inData.length; i++) {
        sb.append(Integer.toString((inData[i] & 0xff) + 0x100, 16).substring(1));
    }
    println("0x" + sb.toString());
}

CArgs theArgs = parse_cmdline(args)
byte[] img_id = write_header(theArgs)
write_data(theArgs)
if (theArgs.id) {
    ByteBuffer bf = ByteBuffer.allocate(32);
    bf.order(ByteOrder.LITTLE_ENDIAN);
    bf.put(img_id);
    bf.put(new byte[32 - img_id.length])
    dumpBytes(bf.array());
}
