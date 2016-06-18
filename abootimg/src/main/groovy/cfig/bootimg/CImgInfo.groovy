package cfig.bootimg

import groovy.json.JsonSlurper
import groovy.transform.ToString

/**
 * Created by yu at 09:58 on 2016-06-17
 */
@ToString(includeNames=true, includeFields=true, includeSuper = true)
class CImgInfo  extends CArgs {
    public int kernel_len;
    public int ramdisk_len;
    public int second_len;
    public int kernel_pos;
    public int ramdisk_pos;
    public int second_pos;
    public byte[] hash;

    static CImgInfo fromJson(String outFile, String workDir) {
        CImgInfo aArg = new CImgInfo();
        //preset info
        aArg.kernel = workDir + File.separator + aArg.kernel;
        aArg.ramdisk = workDir + File.separator + aArg.ramdisk;
        aArg.second = workDir + File.separator + aArg.second;
        aArg.cfg = workDir + File.separator + aArg.cfg;
        aArg.output = outFile;

        JsonSlurper jsonSlurper = new JsonSlurper()
        Map result = jsonSlurper.parseText(new File(aArg.cfg).text);

        //arg info
        aArg.board = result.bootimg.args.board;
        aArg.cmdline = result.bootimg.args.cmdline;
        aArg.base = Integer.decode(result.bootimg.args.base);
        aArg.kernel_offset = Integer.decode(result.bootimg.args.kernel_offset);
        aArg.ramdisk_offset = Integer.decode(result.bootimg.args.ramdisk_offset);
        aArg.second_offset = Integer.decode(result.bootimg.args.second_offset);
        aArg.tags_offset = Integer.decode(result.bootimg.args.tags_offset);
        aArg.id = true;
        aArg.pagesize = result.bootimg.args.pagesize;
        aArg.os_version = result.bootimg.args.os_version;
        aArg.os_patch_level = result.bootimg.args.os_patch_level;
        //image info
        aArg.kernel_len = result.bootimg.img.kernel_len;
        aArg.ramdisk_len = result.bootimg.img.ramdisk_len;
        aArg.second_len = result.bootimg.img.second_len;
        //adjust preset info
        if (0 == aArg.ramdisk_len) {
            aArg.ramdisk = null;
        }
        if (0 == aArg.second_len) {
            aArg.second = null;
        }

        return aArg;
    }
}
