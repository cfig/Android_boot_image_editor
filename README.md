# Android_boot_image_editor
[![Build Status](https://travis-ci.org/cfig/Android_boot_image_editor.svg?branch=master)](https://travis-ci.org/cfig/Android_boot_image_editor)
[![License](http://img.shields.io/:license-apache-blue.svg?style=flat-square)](http://www.apache.org/licenses/LICENSE-2.0.html)

This tool focuses on editing Android boot.img(also recovery.img and recovery-two-step.img).

## Prerequisite
#### Host OS requirement:

Linux or Mac.

#### Target Android requirement:

(1) Targeted boot.img(or recovery.img / recovery-two-step.img) MUST follows AOSP [verified boot flow](https://source.android.com/security/verifiedboot/index.html), which means it packs linux kernel, rootfs , and a optional second state bootloader, then sign it with OEM/USER keys.

(2) These utilities are known to work for Nexus (or Nexus compatible) boot.img(or recovery.img/recovery-two-step.img) for the following Android releases:

 - AOSP master
 - Lollipop (API Level 21,22) - Oreo (API Level 26,27)

You can get a full [Android version list](https://source.android.com/source/build-numbers.html) here.

## Usage
Get tools via git:

    git clone https://github.com/cfig/Android_boot_image_editor.git
    cd Android_boot_image_editor

Then put your boot.img at **$(CURDIR)/boot.img**, then start gradle 'unpack' task:

    cp <original_boot_image> boot.img
    ./gradlew unpack

Your get the flattened kernel and /root filesystem under **$(CURDIR)/build/unzip\_boot**:

    build/unzip_boot/
    ├── bootimg.json
    ├── kernel
    ├── second
    └── root

Then you can edit the actual file contents, like rootfs or kernel.
Now, pack the boot.img again

    ./gradlew pack

You get the repacked boot.img at $(CURDIR):

    boot.img.signed

#### If you are working with recovery.img
If you are working with recovery.img, the steps are similar:

    cp <original_recovery_image> recovery.img
    ./gradlew unpack
    ./gradlew pack

And you get recovery.img.signed


## example & test
An example boot.img has been placed at **src/test/resources/boot.img**, which is extracted from Nexus 5x(code: bullhead) factory images from [Google](https://dl.google.com/dl/android/aosp/bullhead-mda89e-factory-29247942.tgz), you can take it as a quick start.

## boot.img layout
Read [layout](https://github.com/cfig/Android_boot_image_editor/blob/master/README.expert.md) of Android boot.img.
We now support **os\_version** and **os\_patch\_level**.

## References

boot\_signer
https://android.googlesource.com/platform/system/extras

bouncycastle
https://android.googlesource.com/platform/external/bouncycastle

cpio / fs\_config
https://android.googlesource.com/platform/system/core
