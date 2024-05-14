## Test set up
source code and release package

## boot.img

## ota.zip
platforms: macOS, Linux

specify the partition to extract:
"-Dpart=XXX"

* jdk: java 11

* wrapper script
should be compatible with "/usr/bin/env sh"

## dtbo

[multiple dtb](https://source.android.com/docs/core/architecture/dto/multiple)

## TODO: command line usage
unpack
```
abe unpack boot.img out
```

pack
```
abe pack out boot.img
```
properties: "out.file": the final output file

### something interesting in abe
a zsh script, parse the input  command line parameters, for example, 
if args are: "unpack file dir", the shell script will print 'gradle unpack --args="unpackInternal file dir"'; 
if args are "pack dir file", the shell script will print 'gradle pack --args="packInternal dir file"'.
if args are "unpack", the shell script will print "gradle unpack"
if args are "pack", the shell script will print "gradle pack"

