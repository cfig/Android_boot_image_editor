package cfig.bootimg;

void Run(List<String> inCmd, String inWorkdir = null) {
    println("CMD:" + inCmd)
    if (inWorkdir == null) {
        inWorkdir = ".";
    }
    ProcessBuilder pb = new ProcessBuilder(inCmd)
            .directory(new File(inWorkdir))
            .redirectErrorStream(true);
    Process p = pb.start()
    p.inputStream.eachLine {println it}
    p.waitFor();
    assert 0 == p.exitValue()
}

if (3 != args.length) {
    println("Usage:\n\trepack_with_cmd <out_file> <work_dir> <mkbootimg_bin>");
    println("It will create <out_file> from <work_dir>/kernel, <work_dir>/ramdisk.img.gz and <work_dir>/second");
    println("with the program <mkbootimg_bin>");
    println("Example:\n\trepack boot.img.clear build/unzip_boot mkbootimg");
    System.exit(1);
}

CImgInfo aInfo = CImgInfo.fromJson(args[0], args[1]);
List<String> cmdArgs = aInfo.toCommandList();
int i = 0;
for (String item : args[2].split()) {
    cmdArgs.add(i, item);
    i++;
}
Run(cmdArgs);
