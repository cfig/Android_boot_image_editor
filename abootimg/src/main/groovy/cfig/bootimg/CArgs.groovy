package cfig.bootimg

import groovy.transform.ToString

/**
 * Created by yu at 09:57 on 2016-06-17
 */
@ToString(includeNames = true, includeFields = true)
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

    CArgs() {
        kernel = "kernel";
        ramdisk = "ramdisk.img.gz";
        second = "second";
        output = "boot.img";
        cfg = "bootimg.json";
    }
}
