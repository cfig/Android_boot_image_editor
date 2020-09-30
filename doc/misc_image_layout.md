# /misc partition layout

| - | - | - | Offset | Size |  description |
| :---- | :------ | :---- | :---- | :---- | ----- |
| Legacy      | bootloader_message |   | 0 | (2K) |   |
| | |command |  | 32 |updated by linux/bootloader |
| | |status |  | 32 |deprecated |
| | |recovery | |768 |talking channel between normal/recovery modes |
| |                                              |stage |  | 32 |format "#/#", eg, "1/3" |
| | |reserved | | 1184| |
| - | - |- | - | - | - |
| Vendor Area | vendor bootloader msg | N/A | 2K | 2K | Vendor Area                                                  |
|             | vendor bootloader msg | N/A | 4K | 12K | pure Vendor area                                             |
| -           | - | - | - | - | - |
| Wipe        | wipe_package info                            | | 16K | 16K | offset 16KB, Used by uncrypt and recovery to store wipe_package for A/B devices |
| -           | -                                            | - | - | - | - |
| System      | system_space -> 1<br>misc_virtual_ab_message | |  | (64) | |
| |  | version |  | 1 | |
| |  | magic |  | 4 | |
| |  | merge_status |  | 1 | |
| |  | source_slot |  | 1 | |
| |  | reserved |  | 57 | |
| |  |  | | | |
| |  |  | |  | |



### vendor area implementation example from Google: 


#### bootctrl.default.so (link libboot_control.a)貌似没人用


使用legacy_boot_control.cpp, 把libboot_control.a的实现的类android::bootable::BootControl包装进去



### android.hardware.boot@1.1-impl

使用BootControl.cpp

```
android::hardware::boot::V1_1::implementation::BootControl
```
直接使用"android::bootable::BootControl"的实现








#### libboot_control.a

code location:

```hardware/interfaces/boot/1.1/default/boot_control```

https://android.googlesource.com/platform/hardware/interfaces/+/refs/heads/master/boot/1.1/default/boot_control/

| - | - | - | Offset | Size |  description |
| :---- | :------ | :---- | :---- | :---- | ----- |
| Vendor Area | bootloader_message_ab | | 2K | (2K) | Vendor Area                                                  |
| |                                              | slot_suffix | | 32| |
| |                                              | update_channel | |128 | |
| | | reserved | | 1888| |



### 32 BYTES "slot_suffix" part details:

| - | - | - | Offset | Size |  description |
| :---- | :------ | :---- | :---- | :---- | ----- |
| | bootloader_control |  | | (32) | |
| |  | slot_suffix | | 4 | |
| |  | magic | | 4 | |
| |  | version | | 1 | |
| |  | nb_slot | | 3bits | number slots |
| |  | recovery_tries_remaining | | 3bits | |
| |  | merge_status | | 3bits | |
| |  | reserved0 | | 1 | |
| |  | slot_info | | 8 | slot_metadata * 4 |
| |  | reverved1 | | 8 | |
| |  | crc32_le | | 4 | |
| |  |  | |  | |
| | slot_metadata | | | (2) | |
| | | priority |  | 4bits | |
| | | tries_remaining |  | 3bits | |
| | | successful_boot | | 1bit | |
| | | verity_corrupted | | 1bit | |
| | | reserved | | 7bits | |
| | | | |  | |

