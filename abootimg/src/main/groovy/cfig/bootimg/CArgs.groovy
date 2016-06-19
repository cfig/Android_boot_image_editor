package cfig.bootimg

import groovy.transform.ToString

/**
 * Created by yu at 09:57 on 2016-06-17
 */
@ToString(includeNames = true, includeFields = true, excludes = "toCommandLine, CArgs")
class CArgs {
    public String kernel;
    public String ramdisk;
    public String output;
    public String cfg;
    public String board;
    public String second;
    public String cmdline;
    public String os_version;
    public String os_patch_level;
    public int base;
    public int kernel_offset;
    public int ramdisk_offset;
    public int second_offset;
    public int pagesize;
    public int tags_offset;
    public boolean id;

    public CArgs() {
        kernel = "kernel";
        ramdisk = "ramdisk.img.gz";
        second = "second";
        output = "boot.img";
        cfg = "bootimg.json";
    }

    public List<String> toCommandList() {
        List<String> ret = new ArrayList<String>();
        ret.add("--base");
        ret.add("0x" + Integer.toHexString(base));
        ret.add("--kernel");
        ret.add(kernel);
        ret.add("--kernel_offset");
        ret.add("0x" + Integer.toHexString(kernel_offset));
        if (null != ramdisk) {
            ret.add("--ramdisk");
            ret.add(ramdisk);
        }
        ret.add("--ramdisk_offset");
        ret.add("0x" + Integer.toHexString(ramdisk_offset));
        if (null != second) {
            ret.add("--second");
            ret.add(second);
        }
        ret.add("--second_offset");
        ret.add("0x" + Integer.toHexString(second_offset));
        if (null != board) {
            ret.add("--board");
            ret.add(board);
        }
        ret.add("--pagesize");
        ret.add(Integer.toString(pagesize));
        ret.add("--cmdline");
        ret.add(cmdline);
        if (null != os_version) {
            ret.add("--os_version");
            ret.add(os_version);
        }
        if (null != os_patch_level) {
            ret.add("--os_patch_level");
            ret.add(os_patch_level);
        }
        ret.add("--tags_offset");
        ret.add("0x" + Integer.toHexString(tags_offset));
        if (id) {
            ret.add("--id");
        }
        ret.add("--output");
        ret.add(output);

        return ret;
    }
}
