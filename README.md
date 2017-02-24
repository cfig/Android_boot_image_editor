# Android_boot_image_editor
[![Build Status](https://travis-ci.org/cfig/Android_boot_image_editor.svg?branch=master)](https://travis-ci.org/cfig/Android_boot_image_editor)

Utilies for editing Android boot.img or recovery.img.

## Prerequisite
#### Host OS requirement:

Linux or Mac.

#### Target Android requirement:

(1) Targeted boot.img(or recovery.img) MUST follows AOSP [verified boot flow](https://source.android.com/security/verifiedboot/index.html), which means it packs linux kernel, rootfs , and a optional second state bootloader, then sign it with OEM/USER keys.

(2) These utilities are known to work for Nexus (or Nexus compatible) boot.img(or recovery.img) for the following Android releases:

 - Marshmallow (API Level 23)
 - Lollipop (API Level 21,22)
 - AOSP master

You can get a full [Android version list](https://source.android.com/source/build-numbers.html) here.

## Usage
Get tools via git:

    git clone https://github.com/cfig/Android_boot_image_editor.git
    cd Android_boot_image_editor

Then put your boot.img at **$(CURDIR)/boot.img**, then start gradle 'unpack' task:

    cp <original_boot_image> boot.img
    ./gradew unpack

Your get the flattened kernel and /root filesystem under **$(CURDIR)/build/unzip\_boot**:

    build/unzip_boot/
    ├── bootimg.json
    ├── kernel
    ├── second
    └── root

Then you can edit the actual file contents, like rootfs or kernel.
Now, pack the boot.img again

    ./gradew pack

You get the repacked boot.img at $(CURDIR):

    boot.img.signed

#### If you are working with recovery.img
If you are working with recovery.img, the steps are similar:

    cp <original_recovery_image> recovery.img
    ./gradew unpack
    ./gradew pack

And you get recovery.img.signed


## example & test
An example boot.img has been placed at **src/test/resources/boot.img**, which is extracted from Nexus 5x(code: bullhead) factory images from [Google](https://dl.google.com/dl/android/aosp/bullhead-mda89e-factory-29247942.tgz), you can take it as a quick start.

## boot.img layout
Read [layout](https://github.com/cfig/Android_boot_image_editor/blob/master/README.expert.md) of Android boot.img.
We now support **os\_version** and **os\_patch\_level**.
