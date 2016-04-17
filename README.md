# Nexus_boot_image_editor
[![Build Status](https://travis-ci.org/cfig/Nexus_boot_image_editor.svg?branch=master)](https://travis-ci.org/cfig/Nexus_boot_image_editor)

Utilies for editing Nexus(or Nexus compatible) devices boot.img , then you don't need full Android source code to edit your boot images.

## Prerequisite
#### Host OS requirement:

The unpacking task only works on Linux, the packing task can work on Linux & OSX.
So the recommended OS is Linux.

#### Target Android requirement:

(1) Targeted boot.img MUST follows AOSP [verified boot flow](https://source.android.com/security/verifiedboot/index.html), which means it packs linux kernel and rootfs together, then sign it with OEM/USER keys.

(2) These utilities are known to work for Nexus (or Nexus compatible) boot.img for the following Android releases:

 - Marshmallow (API Level 23)
 - Lollipop (API Level 21,22)

You can get a full [Android version list](https://source.android.com/source/build-numbers.html) here.

## Usage
First put your boot.img at **$(CURDIR)/boot.img**, then start gradle 'unpack' task:

    cp <original_boot_image> boot.img
    ./gradew unpack

Your get the flattened kernel and /root filesystem under **$(CURDIR)/build/unzip\_boot**:

    build/unzip_boot/
    ├── bootimg.cfg
    ├── kernel
    └── root

Then you can edit the actual file contents, like rootfs or kernel.
Now, pack the boot.img again

    ./gradew pack

You get the repacked boot.img at $(CURDIR):

    boot.img.signed

## example & test
An example boot.img has been placed at **src/test/resources/boot.img**, which is extracted from Nexus 5x(code: bullhead) factory images from [Google](https://dl.google.com/dl/android/aosp/bullhead-mda89e-factory-29247942.tgz), you can take it as a quick start.
