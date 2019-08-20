# 中文快速说明

## 解包boot.img
boot.img放在当前目录, 然后

    $ ./gradlew unpack

解出来的文件在build/unzip_boot/, 可以自行更换kernel或者修改rootfs内容和打包参数

## 重新打包

    $ ./gradlew pack

新生成的文件名boot.img.signed

