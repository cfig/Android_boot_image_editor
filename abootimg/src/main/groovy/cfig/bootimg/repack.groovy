package cfig.bootimg

if (2 != args.length) {
    println("Usage:\n\trepack <out_file> <work_dir>");
    println("It will create <out_file> from <work_dir>/kernel, <work_dir>/ramdisk.img.gz and <work_dir>/second");
    println("Example:\n\trepack boot.img.clear build/unzip_boot");
    System.exit(1);
}

CImgInfo aInfo = CImgInfo.fromJson(args[0], args[1]);
new Packer().mkbootimg(aInfo);
