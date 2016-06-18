package cfig.bootimg

// http://mvnrepository.com/artifact/net.sf.jopt-simple/jopt-simple
//@Grapes(
//    @Grab(group='net.sf.jopt-simple', module='jopt-simple', version='5.0.1')
//)
Packer thePacker = new Packer();
CArgs theArgs = thePacker.parse_cmdline(args)
thePacker.mkbootimg(theArgs);
