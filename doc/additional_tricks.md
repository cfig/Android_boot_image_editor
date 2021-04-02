## tricks for Nexus 9(volantis)

**volantis** has a dummy header of size 256 bytes, which looks like this:

    0000000: 78 56 34 12 00 00 00 00 00 ba 86 00 00 01 00 00  xV4.............
    0000010: 00 01 00 00 00 b8 86 00 00 b9 86 00 00 01 00 00  ................
    0000020: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00  ................
    0000030: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00  ................
    0000040: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00  ................
    0000050: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00  ................
    0000060: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00  ................
    0000070: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00  ................
    0000080: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00  ................
    0000090: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00  ................
    00000a0: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00  ................
    00000b0: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00  ................
    00000c0: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00  ................
    00000d0: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00  ................
    00000e0: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00  ................
    00000f0: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00  ................
    0000100: 41 4e 44 52 4f 49 44 21 72 64 6d 00 00 80 00 10  ANDROID!rdm.....
    0000110: d0 41 19 00 00 00 00 11 00 00 00 00 00 00 f0 10  .A..............

We have to trim the header before it can be recognized by our toy.

    $ dd if=boot.img of=raw_boot bs=256 skip=1

Now we can work with 'raw\_boot'

    $ cp raw_boot boot.img
    $ gradle unpack
    $ gradle pack

## Pixel XL (marlin)

**marlin** is a profile that adopts A/B system schema while still using Verified Boot 1.0 style boot image.

Due to the configuration "BOARD_BUILD_SYSTEM_ROOT_IMAGE := true", the embeded ramdisk in boot.img is actually used in recovery mode.

## Pixel 3 (blueline)

Fickle Google removed "BOARD_BUILD_SYSTEM_ROOT_IMAGE" and added "ro.boot.dynamic_partitions=true", which means normal mode ramdisk is back. Besides, it also packed DTB inside boot.img.

## NX606J

Thanks to the work by [CallMESuper], ZTE NX606J boot.img is also verified to be compatible with this toolkit.

ROM download page: [http://ui.nubia.cn/rom/detail/56](http://ui.nubia.cn/rom/detail/56)

## K3 (CPH1955)

`boot.img` extracted from OTA zip file doesn't work properly but `recovery.img` works fine. In order to obtain `recovery.img`, a `bsdiff` patch from `system/recovery-from-boot.p` is applied to `boot.img`. Ex: ```bspatch boot.img recovery.img system/recovery-from-boot.p```

This part is contributed by @Surendrajat, thanks!

## about porting

#### libsparse: output\_file.cpp

*typeof* is missing in macos clang++, need to change it to *decltype* instead.

## using pre-packed ramdisk.img.gz
place 'ramdisk.img.gz' in directory, delete "root/", program will use it as prebuilt.

## cpio
decompress cpio with commandline `cpio -idmv -F <file>`

Some file system(also java) doesn't support special file permissions, https://docs.oracle.com/cd/E19455-01/805-7229/secfiles-69/index.html
So we have to save the file perms in `build/unzip_boot/ramdisk_filelist.txt`, and use it when doing 'pack'.

### cpio on windows
* got `java.nio.file.FileSystemException` and says "A required privilege is not held by the client"
```
 java.base/java.nio.file.Files.createSymbolicLink(Files.java:1058)
```
Solution:
Avoid using this feature on Windows, create regular file instead.

* File.renameTo() is problematic, use Files.move() instead.

* remember to close File streams to avoid any potential problems

## Boot image signature in BootImage V4
"boot signature" is designed for GKI, it's to be verified by VTS, not bootloader, so this part can be seen as part of the raw boot.img for bootloader.

Emulate creating GKI image:
```
out/host/linux-x86/bin/mkbootimg --kernel out/target/product/vsoc_arm64/kernel  --ramdisk out/target/product/vsoc_arm64/ramdisk.img --gki_signing_key external/avb/test/data/testkey_rsa4096.pem --gki_signing_algorithm SHA256_RSA4096 --os_version 11 --os_patch_level 2021-03-05 --header_version 4 --output out/target/product/vsoc_arm64/boot.img
out/host/linux-x86/bin/avbtool add_hash_footer --image out/target/product/vsoc_arm64/boot.img --partition_size   67108864 --partition_name boot --algorithm SHA256_RSA2048 --key external/avb/test/data/testkey_rsa2048.pem --prop com.android.build.boot.fingerprint:nicefinger --prop com.android.build.boot.os_version:11 --rollback_index 1614902400
```
As it's only used for GKI verification, I don't want to spend too much time on any special steps in 'gradle pack' flow, as long as DUT can boot up properly.

