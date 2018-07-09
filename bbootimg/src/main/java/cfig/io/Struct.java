package cfig.io;

import cfig.Helper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;

public class Struct {
    private static Logger log = LoggerFactory.getLogger(Struct.class);

    private ByteOrder byteOrder = ByteOrder.LITTLE_ENDIAN;
    private List<Object[]> formats = new ArrayList<>();

    public Struct(String formatString) {
        Matcher m = Pattern.compile("(\\d*)([a-zA-Z])").matcher(formatString);

        if (formatString.startsWith(">") || formatString.startsWith("!")) {
            this.byteOrder = ByteOrder.BIG_ENDIAN;
            log.debug("Parsing BIG_ENDIAN format: " + formatString);
        } else {
            log.debug("Parsing LITTLE_ENDIAN format: " + formatString);
        }

        while (m.find()) {
            boolean bExpand = true;
            int mul = 1;
            if (!m.group(1).isEmpty()) {
                mul = Integer.decode(m.group(1));
            }
            Object item[] = new Object[2];
            switch (m.group(2)) {
                case "x": {//byte 1
                    item[0] = PadByte.class;
                    bExpand = false;
                    break;
                }
                case "b": {//byte 1
                    item[0] = Byte.class;
                    bExpand = false;
                    break;
                }
                case "s": {//python: char 1
                    item[0] = Character.class;
                    bExpand = false;
                    break;
                }
                case "h": {//2
                    item[0] = Short.class;
                    break;
                }
                case "H": {//2
                    item[0] = UnsignedShort.class;
                    break;
                }
                case "i":
                case "l": {//4
                    item[0] = Integer.class;
                    break;
                }
                case "I":
                case "L": {//4
                    item[0] = UnsignedInt.class;
                    break;
                }
                case "q": {//8
                    item[0] = Long.class;
                    break;
                }
                case "Q": {//8
                    item[0] = UnsignedLong.class;
                    break;
                }
                default: {
                    throw new IllegalArgumentException("type [" + m.group(2) + "] not supported");
                }
            }
            if (bExpand) {
                item[1] = 1;
                for (int i = 0; i < mul; i++) {
                    formats.add(item);
                }
            } else {
                item[1] = mul;
                formats.add(item);
            }
        }
    }

    public Integer calcSize() {
        Integer ret = 0;
        for (Object[] format : formats) {
            if (format[0] == Byte.class || format[0] == Character.class || format[0] == PadByte.class) {
                ret += (int) format[1];
                continue;
            }
            if (format[0] == Short.class) {
                ret += 2 * (int) format[1];
                continue;
            }
            if (format[0] == UnsignedShort.class) {
                ret += 2 * (int) format[1];
                continue;
            }
            if (format[0] == Integer.class) {
                ret += 4 * (int) format[1];
                continue;
            }
            if (format[0] == UnsignedInt.class) {
                ret += 4 * (int) format[1];
                continue;
            }
            if (format[0] == Long.class || format[0] == UnsignedLong.class) {
                ret += 8 * (int) format[1];
                continue;
            }
            throw new IllegalArgumentException("Class [" + format[0] + "] not supported");
        }
        return ret;
    }

    public void dump() {
        log.info("--- Format ---");
        log.info("Endian: " + this.byteOrder);
        for (Object[] formatItem : formats) {
            log.info(formatItem[0] + ":" + formatItem[1]);
        }
        log.info("--- Format ---");
    }

    public List unpack(InputStream iS) throws IOException {
        List<Object> ret = new ArrayList<>();
        ByteBuffer bf = ByteBuffer.allocate(32);
        bf.order(this.byteOrder);
        for (Object[] format : this.formats) {
            //return 'null' for padding bytes
            if (format[0] == PadByte.class) {
                long skipped = iS.skip((Integer) format[1]);
                assertEquals((long) (Integer) format[1], skipped);
                ret.add(null);
                continue;
            }

            if (format[0] == Byte.class || format[0] == Character.class || format[0] == PadByte.class) {
                byte[] data = new byte[(Integer) format[1]];
                assertEquals((int) format[1], iS.read(data));
                ret.add(data);
                continue;
            }

            if (format[0] == Short.class) {
                byte[] data = new byte[2];
                assertEquals(2, iS.read(data));
                bf.clear();
                bf.put(data);
                bf.flip();
                ret.add(bf.getShort());
                continue;
            }

            if (format[0] == UnsignedShort.class) {
                byte[] data = new byte[2];
                assertEquals(2, iS.read(data));
                log.debug("UnsignedShort: " + Helper.Companion.toHexString(data));
                bf.clear();
                if (this.byteOrder == ByteOrder.LITTLE_ENDIAN) {
                    bf.put(data);
                    bf.put(new byte[2]); //complete high bits with 0
                } else {
                    bf.put(new byte[2]); //complete high bits with 0
                    bf.put(data);
                }
                bf.flip();
                ret.add(bf.getInt());
                continue;
            }

            if (format[0] == Integer.class) {
                byte[] data = new byte[4];
                assertEquals(4, iS.read(data));
                log.debug("Integer: " + Helper.Companion.toHexString(data));
                bf.clear();
                bf.put(data);
                bf.flip();
                ret.add(bf.getInt());
                continue;
            }

            if (format[0] == UnsignedInt.class) {
                byte[] data = new byte[4];
                assertEquals(4, iS.read(data));
                bf.clear();
                log.debug("UnsignedInt: " + Helper.Companion.toHexString(data));
                if (this.byteOrder == ByteOrder.LITTLE_ENDIAN) {
                    bf.put(data);
                    bf.put(new byte[4]); //complete high bits with 0
                } else {
                    bf.put(new byte[4]); //complete high bits with 0
                    bf.put(data);
                }
                bf.flip();
                ret.add(bf.getLong());
                continue;
            }

            //TODO: maybe exceeds limits of Long.class ?
            if (format[0] == Long.class || format[0] == UnsignedLong.class) {
                byte[] data = new byte[8];
                assertEquals(8, iS.read(data));
                bf.clear();
                bf.put(data);
                bf.flip();
                ret.add(bf.getLong());
                continue;
            }

            throw new IllegalArgumentException("Class [" + format[0] + "] not supported");
        }
        return ret;
    }

    public byte[] pack(Object... args) {
        if (args.length != this.formats.size()) {
            throw new IllegalArgumentException("argument size " + args.length +
                    " doesn't match format size " + this.formats.size());
        }
        ByteBuffer bf = ByteBuffer.allocate(this.calcSize());
        bf.order(this.byteOrder);
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            Class<?> format = (Class<?>) formats.get(i)[0];
            Integer size = (int) formats.get(i)[1];
            log.debug("Index[" + i + "], fmt = " + format + ", arg = " + arg + ", multi = " + size);

            //padding
            if (format == PadByte.class) {
                byte b[] = new byte[size];
                if (arg == null) {
                    Arrays.fill(b, (byte) 0);
                } else if (arg instanceof Byte) {
                    Arrays.fill(b, (byte) arg);
                } else if (arg instanceof Integer) {
                    Arrays.fill(b, ((Integer) arg).byteValue());
                } else {
                    throw new IllegalArgumentException("Index[" + i + "] Unsupported arg [" + arg + "] with type [" + format + "]");
                }
                bf.put(b);
                continue;
            }

            //signed byte
            if (arg instanceof byte[]) {
                bf.put((byte[]) arg);
                int paddingSize = size - ((byte[]) arg).length;
                if (0 < paddingSize) {
                    byte padBytes[] = new byte[size - ((byte[]) arg).length];
                    Arrays.fill(padBytes, (byte) 0);
                    bf.put(padBytes);
                } else if (0 > paddingSize) {
                    log.error("container size " + size + ", value size " + ((byte[]) arg).length);
                    throw new IllegalArgumentException("Index[" + i + "] arg [" + arg + "] with type [" + format + "] size overflow");
                } else {
                    log.debug("perfect match, paddingSize is zero");
                }
                continue;
            }

            //unsigned byte
            if (arg instanceof int[] && format == Byte.class) {
                for (int v : (int[]) arg) {
                    if (v > 255 || v < 0) {
                        throw new IllegalArgumentException("Index[" + i + "] Unsupported [int array] arg [" + arg + "] with type [" + format + "]");
                    }
                    bf.put((byte) v);
                }
                continue;
            }

            if (arg instanceof Short) {
                bf.putShort((short) arg);
                continue;
            }

            if (arg instanceof Integer) {
                if (format == Integer.class) {
                    bf.putInt((int) arg);
                } else if (format == UnsignedShort.class) {
                    ByteBuffer bf2 = ByteBuffer.allocate(4);
                    bf2.order(this.byteOrder);
                    bf2.putInt((int) arg);
                    bf2.flip();
                    if (this.byteOrder == ByteOrder.LITTLE_ENDIAN) {//LE
                        bf.putShort(bf2.getShort());
                        bf2.getShort();//discard
                    } else {//BE
                        bf2.getShort();//discard
                        bf.putShort(bf2.getShort());
                    }
                } else if (format == UnsignedInt.class) {
                    if ((Integer) arg < 0) {
                        throw new IllegalArgumentException("Index[" + i + "] Unsupported [Integer] arg [" + arg + "] with type [" + format + "]");
                    }
                    bf.putInt((int) arg);
                } else {
                    throw new IllegalArgumentException("Index[" + i + "] Unsupported [Integer] arg [" + arg + "] with type [" + format + "]");
                }
                continue;
            }

            if (arg instanceof Long) {
                //XXX: maybe run into issue if we meet REAL Unsigned Long
                if (format == Long.class || format == UnsignedLong.class) {
                    bf.putLong((long) arg);
                } else if (format == UnsignedInt.class) {
                    if ((Long) arg < 0L || (Long) arg > (Integer.MAX_VALUE * 2L + 1)) {
                        throw new IllegalArgumentException("Index[" + i + "] Unsupported [Long] arg [" + arg + "] with type [" + format + "]");
                    }
                    ByteBuffer bf2 = ByteBuffer.allocate(8);
                    bf2.order(this.byteOrder);
                    bf2.putLong((long) arg);
                    bf2.flip();
                    if (this.byteOrder == ByteOrder.LITTLE_ENDIAN) {//LE
                        bf.putInt(bf2.getInt());
                        bf2.getInt();//discard
                    } else {//BE
                        bf2.getInt();//discard
                        bf.putInt(bf2.getInt());
                    }
                } else {
                    throw new IllegalArgumentException("Index[" + i + "] Unsupported arg [" + arg + "] with type [" + format + "]");
                }
            }
        }
        log.debug("Pack Result:" + Helper.Companion.toHexString(bf.array()));
        return bf.array();
    }

    private static class UnsignedInt {
    }

    private static class UnsignedLong {
    }

    private static class UnsignedShort {
    }

    private static class PadByte {
    }
}
