package cfig.bootimg

import groovy.transform.ToString

/**
 * Created by yu at 09:58 on 2016-06-17
 */
@ToString(includeNames=true, includeFields=true)
class CImgInfo  extends CArgs {
    public int kernel_len;
    public int ramdisk_len;
    public int second_len;
    public int kernel_pos;
    public int ramdisk_pos;
    public int second_pos;
    public byte[] hash;
    public String dump() {
        return super.toString() + " ; " + toString();
    }
}
