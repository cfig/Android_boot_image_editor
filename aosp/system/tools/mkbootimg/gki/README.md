# GKI boot image retrofitting tools for upgrading devices

Starting from Android T the GKI boot images consist of the generic `boot.img`
and `init_boot.img`. The `boot.img` contains the generic kernel, and
`init_boot.img` contains the generic ramdisk.
For upgrading devices whose `vendor_boot` partition is non-existent, this tool
(or spec) can be used to retrofit a set of Android T GKI `boot`, `init_boot` and
OEM `vendor_boot` partition images back into a single boot image containing the
GKI kernel plus generic and vendor ramdisks.

## Retrofitting the boot images

1. Download the certified GKI `boot.img`.
2. Go to the build artifacts page of `aosp_arm64` on `aosp-master` branch on
   https://ci.android.com/ and download `gki_retrofitting_tools.zip`.
3. Unzip and make sure the tool is in `${PATH}`.

   ```bash
   unzip gki_retrofitting_tools.zip
   export PATH="$(pwd)/gki_retrofitting_tools:${PATH}"
   # See tool usage:
   retrofit_gki --help
   ```

4. Create the retrofitted image. The `--version` argument lets you choose the
   boot image header version of the retrofitted boot image. Only version 2 is
   supported at the moment.

   ```bash
   retrofit_gki --boot boot.img --init_boot init_boot.img \
     --vendor_boot vendor_boot.img --version 2 -o boot.retrofitted.img
   ```

## Spec of the retrofitted images

* The SOURCE `boot.img` must be officially certified Android T (or later) GKI.
* The DEST retrofitted boot image must not set the security patch level in its
  header. This is because the SOURCE images might have different SPL value, thus
  making the boot header SPL of the retrofitted image ill-defined. The SPL value
  must be defined by the chained vbmeta image of the `boot` partition.
* The `boot signature` of the DEST image is the `boot signature` of the DEST
  `boot.img`.
* The DEST retrofitted boot image must pass the `vts_gki_compliance_test`
  testcase.

### Retrofit to boot image V2

* The `kernel` of the DEST image must be from the SOURCE `boot.img`.
* The `ramdisk` of the DEST image must be from the SOURCE `vendor_boot.img` and
  `init_boot.img`. The DEST `ramdisk` is the ramdisk concatenation of the vendor
  ramdisk and generic ramdisk.
* The `recovery dtbo / acpio` must be empty.
* The `dtb` of the DEST image must be from the SOURCE `vendor_boot.img`.
* The `boot_signature` section must be appended to the end of the boot image,
  and its size is zero-padded to 16KiB.

```
  +---------------------+
  | boot header         | 1 page
  +---------------------+
  | kernel              | n pages
  +---------------------+
  | * vendor ramdisk    |
  |  +generic ramdisk   | m pages
  +---------------------+
  | second stage        | o pages
  +---------------------+
  | recovery dtbo/acpio | 0 byte
  +---------------------+
  | dtb                 | q pages
  +---------------------+
  | * boot signature    | 16384 (16K) bytes
  +---------------------+
```
