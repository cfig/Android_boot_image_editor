# /misc partition layout

| - | - | - | size |  description |
| :---- | :------ | :---- | :---- | :---- |
| bootloader_message_ab |  |   | 4096 | |
|   | bootloader_message |   | 2048 |   | 
| | |command | 32 |updated by linux/bootloader |
| | |status | 32 |deprecated |
| | |recovery |768 |talking channel between normal/recovery modes |
| | |stage | 32 |format "#/#", eg, "1/3" |
| | |reserved | 1184| |
| |slot_suffix | | 32| |
| |update_channel | |128 | |
| |reserved | | 1888| |
| | | | | |
| vendor bootloader msg | | | 12 KB | offset 4kB |
| wipe_package info | | | 48K | offset 16KB, Used by uncrypt and recovery to store wipe_package for A/B devices |
|- |- |- |- |- |
| | | | | |

