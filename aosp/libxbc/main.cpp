#include <fcntl.h>
#include <libxbc.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>

static_assert(sizeof(uint64_t) == 8, "uint64_t 8 bytes");
static_assert(sizeof(void*) == 8, "void* 8 bytes");

void dumpBuf(void* buf, size_t bufSize, const char* outFile) {
    int flags = O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC;
    printf("dumping buffer to %s...\n", outFile);
    auto fd = open(outFile, flags, 0644);
    if (fd == -1) {
        printf("fail to open file %s(%s)\n", outFile, strerror(errno));
        exit(1);
    }
    size_t bytesWrite = write(fd, (char*)buf, bufSize);
    if (bytesWrite != bufSize) {
        printf("write failed. exp=%zu, act=%ld\n", bufSize, bytesWrite);
        exit(2);
    }
    close(fd);
}

int main(int, char**) {
    size_t bufSize = 256;
    uint64_t buf = reinterpret_cast<uint64_t>(malloc(bufSize));
    uint32_t bootConfigSize = 0;
    if (!buf) {
        printf("malloc failed\n");
        exit(1);
    }

    {  // param1
        char* ANDROID_BOOT_PARAM = (char*)"androidboot.xx=yy\n";
        auto ret = addBootConfigParameters(ANDROID_BOOT_PARAM, strlen(ANDROID_BOOT_PARAM), buf,
                                           bootConfigSize);
        if (ret <= 0) {
            printf("fail to apply boot config params\n");
            exit(1);
        } else {
            printf("addBootConfigParameters() ret = %d\n", ret);
            bootConfigSize += ret;
        }
        dumpBuf((void*)buf, bufSize, "tmp.1");
    }

    {  // param2
        char* param2 = (char*)"k1=v1\nk2=v2\nk3=v3\n";
        auto ret = addBootConfigParameters(param2, strlen(param2), buf, bootConfigSize);
        if (ret <= 0) {
            printf("fail to apply boot config param2\n");
            exit(1);
        } else {
            printf("addBootConfigParameters() ret = %d\n", ret);
            bootConfigSize += ret;
        }
        dumpBuf((void*)buf, bufSize, "tmp.2");
    }

    {  // param3
        char* param3 =
                (char*)"vendorboot_k1=vendorboot_v1\nvendorboot_k2=vendorboot_v2\nvendorboot_k3="
                       "vendorboot_v3\n";
        auto ret = addBootConfigParameters(param3, strlen(param3), buf, bootConfigSize);
        if (ret <= 0) {
            printf("fail to apply boot config param3\n");
            exit(1);
        } else {
            printf("addBootConfigParameters() ret = %d\n", ret);
            bootConfigSize += ret;
        }
        dumpBuf((void*)buf, bufSize, "tmp.3");
        dumpBuf((void*)buf, bootConfigSize, "tmp.final");
    }

    free((void*)buf);
    return 0;
}
