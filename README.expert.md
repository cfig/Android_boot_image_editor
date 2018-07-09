# layout of boot.img

### Image Content Index

[1 header part](#1-header-part)

[2 data part](#2-data-part)

[3 signature part](#3-signature-part)

 - [3.1 Boot Image Signature](#31-boot-image-signature-vboot-10)

 - [3.2 AVB Footer](#32-avb-footer-vboot-20)

### 1. header part

              item                        size in bytes             position
    +----------------------------------------------------------+    --> 0
    |<MAGIC HEADER>                  |     8                   |
    |--------------------------------+-------------------------|    --> 8
    |<kernel length>                 |     4                   |
    |--------------------------------+-------------------------|    --> 12
    |<kernel offset>                 |     4                   |
    |--------------------------------+-------------------------|    --> 16 (0x10)
    |<ramdisk length>                |     4                   |
    |--------------------------------+-------------------------|    --> 20
    |<ramdisk offset>                |     4                   |
    |--------------------------------+-------------------------|    --> 24
    |<second bootloader length>      |     4                   |
    |--------------------------------+-------------------------|    --> 28
    |<second bootloader offset>      |     4                   |
    |--------------------------------+-------------------------|    --> 32 (0x20)
    |<tags offset>                   |     4                   |
    |--------------------------------+-------------------------|    --> 36
    |<page size>                     |     4                   |
    |--------------------------------+-------------------------|    --> 40
    |<header version>                |     4                   |
    |--------------------------------+-------------------------|    --> 44
    |<os version& os patch level>    |     4                   |
    |--------------------------------+-------------------------|    --> 48 (0x30)
    |<board name>                    |     16                  |
    |--------------------------------+-------------------------|    --> 64 (0x40)
    |<cmdline part 1>                |     512                 |
    |--------------------------------+-------------------------|    --> 576 (0x240)
    |<hash digest>                   |     32                  |
    |--------------------------------+-------------------------|    --> 608 (0x260)
    |<cmdline part 2>                |     1024                |
    |--------------------------------+-------------------------|    --> 1632 (0x660)
    |<dtbo length>                   |     4                   |
    |--------------------------------+-------------------------|    --> 1636
    |<dtbo offset>                   |     8                   |
    |--------------------------------+-------------------------|    --> 1644
    |<header size>                   |     4                   |
    |--------------------------------+-------------------------|    --> 1648 (0x670)
    |<padding>                       | min(n * page_zie - 1648)|
    +----------------------------------------------------------+    --> pagesize

### 2. data part

    +----------------------------------------------------------+    --> pagesize
    |<kernel>                        |   kernel length         |
    |--------------------------------+-------------------------|
    |<padding>                       |  min(n * page_zie - len)|
    +----------------------------------------------------------+

    +--------------------------------+-------------------------+
    |<ramdisk>                       |   ramdisk length        |
    |--------------------------------+-------------------------|
    |<padding>                       |  min(n * page_zie - len)|
    +----------------------------------------------------------+

    +--------------------------------+-------------------------+
    |<second bootloader>             | second bootloader length|
    |--------------------------------+-------------------------|
    |<padding>                       |  min(n * page_zie - len)|
    +----------------------------------------------------------+

    +--------------------------------+-------------------------+
    |<recovery dtbo>                 | recovery dtbo length    |
    |--------------------------------+-------------------------|
    |<padding>                       |  min(n * page_zie - len)|
    +----------------------------------------------------------+    --> end of data part

### 3. signature part

#### 3.1 Boot Image Signature (VBoot 1.0)

    +--------------------------------+-------------------------+    --> end of data part
    |<signature>                     | signature length        |
    |--------------------------------+-------------------------|
    |<padding>                       | defined by boot_signer  |
    +--------------------------------+-------------------------+

#### 3.2 AVB Footer (VBoot 2.0)

                         item                        size in bytes             position
    +------+--------------------------------+-------------------------+ --> end of data part (say locaton +0)
    |      | VBMeta Header                  | total 256               |
    |      |                                |                         |
    |      |   - Header Magic "AVB0"        |     4                   |
    |      |   - avb_version Major          |     4                   |
    |      |   - avb_version Minor          |     4                   |
    |      |   - authentication blob size   |     8                   |
    |      |   - auxiliary blob size        |     8                   |
    |      |   - algorithm type             |     4                   |
    |      |   - hash_offset                |     8                   |
    |      |   - hash_size                  |     8                   |
    |      |   - signature_offset           |     8                   |
    |      |   - signature_size             |     8                   |
    |      |   - pub_key_offset             |     8                   |
    |VBMeta|   - pub_key_size               |     8                   |
    | Blob |   - pub_key_metadata_offset    |     8                   |
    |      |   - pub_key_metadata_size      |     8                   |
    |      |   - descriptors_offset         |     8                   |
    |      |   - descriptors_size           |     8                   |
    |      |   - rollback_index             |     8                   |
    |      |   - flags                      |     4                   |
    |      |   - RESERVED                   |     4                   |
    |      |   - release string             |     47                  |
    |      |   - NULL                       |     1                   |
    |      |   - RESERVED                   |     80                  |
    |      |--------------------------------+-------------------------+ --> + 256
    |      | Authentication Blob            |                         |
    |      |   - Hash of Header & Aux Blob  | alg.hash_num_bytes      |
    |      |   - Signature of Hash          | alg.signature_num_bytes |
    |      |   - Padding                    | align by 64             |
    |      +--------------------------------+-------------------------+
    |      | Auxiliary Blob                 |                         |
    |      |   - descriptors                |                         | --> + 256 + descriptors_offset
    |      |   - pub key                    |                         | --> + 256 + pub_key_offset
    |      |   - pub key meta data          |                         | --> + 256 + pub_key_metadata_offset
    |      |   - padding                    | align by 64             |
    |      +--------------------------------+-------------------------+
    |      | Padding                        | align by block_size     |
    +------+--------------------------------+-------------------------+ --> + (block_size * n)

    +---------------------------------------+-------------------------+
    |                                       |                         |
    |                                       |                         |
    | DONOT CARE CHUNK                      |                         |
    |                                       |                         |
    |                                       |                         |
    +--------------------------------------- -------------------------+

    +---------------------------------------+-------------------------+ --> partition_size - block_size
    | Padding                               | block_size - 64         |
    +---------------------------------------+-------------------------+ --> partition_size - 64
    | AVB Footer                            | total 64                |
    |                                       |                         |
    |   - Footer Magic "AVBf"               |     4                   |
    |   - Footer Major Version              |     4                   |
    |   - Footer Minor Version              |     4                   |
    |   - Original image size               |     8                   |
    |   - VBMeta offset                     |     8                   |
    |   - VBMeta size                       |     8                   |
    |   - Padding                           |     28                  |
    +---------------------------------------+-------------------------+ --> partition_size
