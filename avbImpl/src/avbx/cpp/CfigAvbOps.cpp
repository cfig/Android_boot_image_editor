//
// Created by yu on 8/30/19.
//
#include <set>
#include <string>
#include <system_error>
#include <iostream>
#include <cstring>
#include <unistd.h>
#include <fcntl.h>
#include <fstream>
#include <sstream>
#include <vector>
#include <sys/stat.h>
#include <dirent.h>

#include <CfigAvbOps.h>

static std::set<std::string> validPartitions;
static std::map<std::string, uint8_t *> preloaded_partitions_;
static std::string expected_public_key_;
static std::string expected_public_key_metadata_;

static auto lockStatusFile = "config/locked";
static auto pubkeyFile = "config/pubkey";

static std::string read_line(std::string file);

static size_t get_file_size(const char *filename);

static std::string getPartitionFile(std::string partition) {
    return std::string(partition) + ".img";
}

static size_t get_file_size(const char *filename) {
    struct stat st{};
    if (stat(filename, &st) != 0) {
        return -1;
    }
    return st.st_size;
}

static AvbIOResult read_is_device_unlockedX(AvbOps *, bool *out_is_unlocked) {
    std::string line = read_line(lockStatusFile);
    if ("0" == line) {
        *out_is_unlocked = true;
        std::cout << "[" << __FUNCTION__ << "], device is unlocked" << std::endl;
    } else {
        *out_is_unlocked = false;
        std::cout << "[" << __FUNCTION__ << "], device is locked" << std::endl;
    }

    return AVB_IO_RESULT_OK;
}

static constexpr char hexmap[] = {'0', '1', '2', '3', '4', '5', '6', '7',
                                  '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

std::string hexStr(const unsigned char *data, int len) {
    std::string s(len * 2, ' ');
    for (int i = 0; i < len; ++i) {
        s[2 * i] = hexmap[(data[i] & 0xF0) >> 4];
        s[2 * i + 1] = hexmap[data[i] & 0x0F];
    }
    return s;
}

bool write_to_file(std::string file, std::string value) {
    std::cout << "write_to_file(file=" << file << ", value=" << value << ")" << std::endl;
    FILE *fp;
    fp = fopen(file.c_str(), "w");
    if (fp == nullptr) {
        fprintf(stderr, "error opening file for writing:%s, (%s)", file.c_str(), strerror(errno));
        return true;
    }
    fprintf(fp, "%s\n", value.c_str());
    fclose(fp);
    return false;
}

static std::string read_line(std::string file) {
    std::ifstream ifs;
    std::string line;
    ifs.open(file, std::ifstream::in);
    if (ifs.is_open()) {
        std::getline(ifs, line);
        return line;
    } else {
        std::cerr << "can not open file " << file << std::endl;
        return "";
    }
}

//from: libavb_user/avb_ops_user.cpp
static AvbIOResult write_to_partitionX(AvbOps *,
                                       const char *partition,
                                       int64_t offset,
                                       size_t num_bytes,
                                       const void *buffer) {
    int fd;
    off_t where;
    ssize_t num_written;
    AvbIOResult ret;

    auto partitionFile = getPartitionFile(partition);
    fd = open(partitionFile.c_str(), O_WRONLY);
    if (fd == -1) {
        avb_errorv("Error opening \"", partition, "\" partition.\n", NULL);
        ret = AVB_IO_RESULT_ERROR_IO;
        goto out;
    }

    where = lseek(fd, offset, SEEK_SET);
    if (where == -1) {
        avb_error("Error seeking to offset.\n");
        ret = AVB_IO_RESULT_ERROR_IO;
        goto out;
    }
    if (where != offset) {
        avb_error("Error seeking to offset.\n");
        ret = AVB_IO_RESULT_ERROR_RANGE_OUTSIDE_PARTITION;
        goto out;
    }

    /* On Linux, we never get partial writes on block devices. */
    num_written = write(fd, buffer, num_bytes);
    if (num_written == -1) {
        avb_error("Error writing data.\n");
        ret = AVB_IO_RESULT_ERROR_IO;
        goto out;
    }

    ret = AVB_IO_RESULT_OK;

    out:
    if (fd != -1) {
        if (close(fd) != 0) {
            avb_error("Error closing file descriptor.\n");
        }
    }
    return ret;
}

static AvbIOResult get_size_of_partitionX(AvbOps *,
                                          const char *partition,
                                          uint64_t *out_size_num_bytes) {
    auto partitionFile = getPartitionFile(partition);
    if (validPartitions.find(partitionFile) == validPartitions.end()) {
        std::cout << "[" << __FUNCTION__ << "(" << partition << ")]: NO_SUCH_PARTITION" << std::endl;
        return AVB_IO_RESULT_ERROR_NO_SUCH_PARTITION;
    }
    auto file_size = get_file_size(partitionFile.c_str());
    if (-1 == file_size) {
        std::cout << "[" << __FUNCTION__ << "(" << partition << ")]: ";
        std::cout << ": error when accessing file [" << partitionFile << "]" << std::endl;
        return AVB_IO_RESULT_ERROR_IO;
    } else {
        std::cout << "[" << __FUNCTION__ << "(" << partition << ")]: ";
        std::cout << ": partition " << partitionFile << " size: " << file_size << std::endl;
        if (out_size_num_bytes != nullptr) {
            *out_size_num_bytes = file_size;
        } else {
            std::cerr << "[" << __FUNCTION__ << "(" << partition << ")]: ";
            std::cerr << ": size is not passed back" << std::endl;
        }
    }
    return AVB_IO_RESULT_OK;
}

static AvbIOResult read_from_partitionX(AvbOps *,
                                        const char *partition,
                                        int64_t offset,
                                        size_t num_bytes,
                                        void *buffer,
                                        size_t *out_num_read) {
    std::cout << "[" << __FUNCTION__ << "(partition=" << partition << ", offset=" << offset << ", num_bytes = "
              << num_bytes
              << ")]:" << std::endl;
    auto partitionFile = getPartitionFile(partition);
    if (validPartitions.find(partitionFile) == validPartitions.end()) {
        std::cout << "[" << __FUNCTION__ << "(" << partition << ")]: NO_SUCH_PARTITION" << std::endl;
        return AVB_IO_RESULT_ERROR_NO_SUCH_PARTITION;
    }
    if (offset < 0) {
        uint64_t file_size;
        auto ret_get_size = get_size_of_partitionX(nullptr, partition, &file_size);
        if (AVB_IO_RESULT_OK != ret_get_size) {
            return ret_get_size;
        }
        offset = file_size - (-offset);
    }
    //open
    int fd = open(partitionFile.c_str(), O_RDONLY);
    if (fd < 0) {
        fprintf(stderr,
                "[%s()]: Error opening file '%s': %s\n",
                __FUNCTION__,
                partitionFile.c_str(),
                strerror(errno));
        if (errno == ENOENT) {
            return AVB_IO_RESULT_ERROR_NO_SUCH_PARTITION;
        } else {
            return AVB_IO_RESULT_ERROR_IO;
        }
    }
    //seek
    if (lseek(fd, offset, SEEK_SET) != offset) {
        fprintf(stderr,
                "[%s()]: Error seeking to pos %ld in file %s: %s\n",
                __FUNCTION__,
                offset,
                partitionFile.c_str(),
                strerror(errno));
        close(fd);
        return AVB_IO_RESULT_ERROR_IO;
    }
    ssize_t num_read = read(fd, buffer, num_bytes);
    if (num_read < 0 || num_read != num_bytes) {
        fprintf(stderr,
                "[%s()]: Error reading %zd bytes from pos %" PRId64 " in file %s: %s\n",
                __FUNCTION__,
                num_bytes,
                offset,
                partitionFile.c_str(),
                strerror(errno));
        close(fd);
        return AVB_IO_RESULT_ERROR_IO;
    }
    close(fd);
    if (out_num_read != nullptr) {
        *out_num_read = num_read;
    }
    fprintf(stdout,
            "[%s()]: Read %ld bytes from partition %s\n",
            __FUNCTION__,
            num_read,
            partition);
//    cout << hexStr((unsigned char *) buffer, num_read) << endl;

    return AVB_IO_RESULT_OK;
}

static AvbIOResult get_preloaded_partitionX(AvbOps *,
                                            const char *partition,
                                            size_t num_bytes,
                                            uint8_t **out_pointer,
                                            size_t *out_num_bytes_preloaded) {
    std::cout << "[" << __FUNCTION__ << "(partition=" << partition << ", num_bytes = " << num_bytes << ")]:"
              << std::endl;
    auto partitionFile = std::string(partition) + ".img";
    if (validPartitions.find(partitionFile) == validPartitions.end()) {
        std::cout << "[" << __FUNCTION__ << "(" << partition << ")]: NO_SUCH_PARTITION" << std::endl;
        return AVB_IO_RESULT_ERROR_NO_SUCH_PARTITION;
    }

    auto it = preloaded_partitions_.find(std::string(partition));
    if (it == preloaded_partitions_.end()) {
        fprintf(stdout, "[%s()]: partition [%s] not preloaded\n", __FUNCTION__, partition);
        *out_pointer = nullptr;
        *out_num_bytes_preloaded = 0;
        return AVB_IO_RESULT_OK;
    }

    uint64_t partSize;
    AvbIOResult result = get_size_of_partitionX(nullptr, partition, &partSize);
    if (result != AVB_IO_RESULT_OK) {
        std::cout << "[" << __FUNCTION__ << "()]: can not get size of partition: (" << partition << ")" << std::endl;
        return result;
    }

    if (num_bytes > partSize) {
        std::cout << "[" << __FUNCTION__ << "()]: size(" << partSize << ") < num_bytes(" << num_bytes << "), can not proceed"
                  << std::endl;
        return AVB_IO_RESULT_ERROR_IO;
    } else if (num_bytes < partSize) {
        *out_num_bytes_preloaded = num_bytes;
    } else {
        //exact match
        *out_num_bytes_preloaded = partSize;
    }

    *out_pointer = it->second;
    return AVB_IO_RESULT_OK;
}

static AvbIOResult validate_vbmeta_public_keyX(AvbOps *,
                                               const uint8_t *public_key_data,
                                               size_t public_key_length,
                                               const uint8_t *public_key_metadata,
                                               size_t public_key_metadata_length,
                                               bool *out_key_is_trusted) {
    if (out_key_is_trusted != nullptr) {
        bool pk_matches = (public_key_length == expected_public_key_.size() &&
                           (memcmp(expected_public_key_.c_str(),
                                   public_key_data,
                                   public_key_length) == 0));
        bool pkmd_matches =
                (public_key_metadata_length == expected_public_key_metadata_.size() &&
                 (memcmp(expected_public_key_metadata_.c_str(),
                         public_key_metadata,
                         public_key_metadata_length) == 0));
        std::cout << "[" << __FUNCTION__ << "(): " << "pk_matches = " << pk_matches << ", pkmd_matches = " << pkmd_matches << std::endl;
        *out_key_is_trusted = pk_matches && pkmd_matches;
    } else {
        std::cout << "[" << __FUNCTION__ << "(out_key_is_trusted = null)]: invalid arg" << std::endl;
    }

    return AVB_IO_RESULT_OK;
}

bool CfigAvbOps::preload_partition(std::string partition) {
    std::cout << "[" << __FUNCTION__ << "(" << partition << ")]:" << std::endl;
    if (preloaded_partitions_.count(partition) > 0) {
        fprintf(stderr, "\t: Partition '%s' already preloaded\n", partition.c_str());
        return false;
    }
    auto partitionFile = getPartitionFile(partition);
    uint64_t file_size;
    auto get_size_ret = get_size_of_partitionX(nullptr, partition.c_str(), &file_size);
    if (get_size_ret != AVB_IO_RESULT_OK) {
        return false;
    }

    int fd = open(partitionFile.c_str(), O_RDONLY);
    if (fd < 0) {
        fprintf(stderr,
                "[%s()]: Error opening file '%s': %s\n",
                __FUNCTION__,
                partitionFile.c_str(),
                strerror(errno));
        return false;
    }

    auto *buffer = static_cast<uint8_t *>(malloc(file_size));
    ssize_t num_read = read(fd, buffer, file_size);
    if (num_read < 0 || num_read != file_size) {
        fprintf(stderr,
                "[%s()]: Error reading %lld bytes from file '%s': %s\n",
                __FUNCTION__,
                file_size,
                partitionFile.c_str(),
                strerror(errno));
        free(buffer);
        return false;
    }
    close(fd);

    preloaded_partitions_[partition] = buffer;

    fprintf(stdout,
            "[%s()]: partition [%s] preloaded\n",
            __FUNCTION__,
            partition.c_str());
    return true;
}

static AvbIOResult read_rollback_indexX(AvbOps *,
                                        size_t rollback_index_location,
                                        uint64_t *out_rollback_index) {
    std::string line = read_line("config/rollbackIndex_" + std::to_string(rollback_index_location));
    if (line.empty()) {
        std::cout << "[" << __FUNCTION__ << "](loc=" << rollback_index_location << "), ret=ERROR_IO" << std::endl;
        return AVB_IO_RESULT_ERROR_IO;
    } else {
        uint64_t value;
        std::istringstream iss(line);
        iss >> value;
        if (out_rollback_index != nullptr) {
            *out_rollback_index = value;
            std::cout << "[" << __FUNCTION__ << "](loc=" << rollback_index_location << "), ret = " << value << std::endl;
        } else {
            std::cout << "[" << __FUNCTION__ << "](loc=" << rollback_index_location << "), ret = " << value
                      << ", value not passed out " << std::endl;
        }
        return AVB_IO_RESULT_OK;
    }
}

static AvbIOResult write_rollback_indexX(AvbOps *,
                                         size_t rollback_index_location,
                                         uint64_t rollback_index) {
    std::cout << "[" << __FUNCTION__ << "](loc=" << rollback_index_location << ", value=" << rollback_index << ")"
              << std::endl;
    if (write_to_file("config/rollbackIndex_" + std::to_string(rollback_index_location),
                      std::to_string(rollback_index))) {
        return AVB_IO_RESULT_OK;
    } else {
        return AVB_IO_RESULT_ERROR_IO;
    }
}

static AvbIOResult get_unique_guid_for_partitionX(AvbOps *,
                                                  const char *partition,
                                                  char *guid_buf,
                                                  size_t guid_buf_size) {
    std::string uuid = "1dddd936-20da-460a-834c-b938a89acab0";
    snprintf(guid_buf, guid_buf_size, "%s-%s", uuid.c_str(), partition);
    std::cout << "[" << __FUNCTION__ << "(" << partition << ")]: set fake value: " << guid_buf << std::endl;
    return AVB_IO_RESULT_OK;
}

//TODO:
static AvbIOResult read_persistent_valueX(AvbOps *,
                                          const char *name,
                                          size_t buffer_size,
                                          uint8_t *out_buffer,
                                          size_t *out_num_bytes_read) {
    std::cout << "[" << __FUNCTION__ << "()]: ret = AVB_IO_RESULT_ERROR_NO_SUCH_VALUE" << std::endl;
    return AVB_IO_RESULT_ERROR_NO_SUCH_VALUE;
}

//TODO:
static AvbIOResult write_persistent_valueX(AvbOps *,
                                           const char *name,
                                           size_t value_size,
                                           const uint8_t *value) {
    std::cout << "[" << __FUNCTION__ << "()]: ret = AVB_IO_RESULT_ERROR_NO_SUCH_VALUE" << std::endl;
    return AVB_IO_RESULT_ERROR_NO_SUCH_VALUE;
}

static AvbIOResult validate_public_key_for_partitionX(
        AvbOps *,
        const char *partition,
        const uint8_t *public_key_data,
        size_t public_key_length,
        const uint8_t *public_key_metadata,
        size_t public_key_metadata_length,
        bool *out_key_is_trusted,
        uint32_t *out_rollback_index_location) {
    std::cout << "[" << __FUNCTION__ << "(partition=" << partition << ")]:" << std::endl;
    if (out_key_is_trusted != nullptr) {
        bool pk_matches = (public_key_length == expected_public_key_.size() &&
                           (memcmp(expected_public_key_.c_str(),
                                   public_key_data,
                                   public_key_length) == 0));
        bool pkmd_matches =
                (public_key_metadata_length == expected_public_key_metadata_.size() &&
                 (memcmp(expected_public_key_metadata_.c_str(),
                         public_key_metadata,
                         public_key_metadata_length) == 0));
        *out_key_is_trusted = pk_matches && pkmd_matches;
    }

    return AVB_IO_RESULT_OK;
}

static void loadPubkey() {
    auto key_file = pubkeyFile;
    std::ifstream ifs(key_file, std::ios::binary | std::ios::ate);
    if (ifs) {
        std::cout << "[" << __FUNCTION__ << "()]:" << "loading key from " << key_file << std::endl;
        std::streamsize size = ifs.tellg();
        ifs.seekg(0, std::ios::beg);
        std::vector<char> buffer(size);
        ifs.read(buffer.data(), size);
        if (ifs.good()) {
            expected_public_key_ = std::string(buffer.begin(), buffer.end());
            std::ofstream ofs("out.key");
            ofs << expected_public_key_;
            ofs.close();
            std::cout << "[" << __FUNCTION__ << "()]:" << "pubkey read finished" << std::endl;
        } else {
            std::cout << "[" << __FUNCTION__ << "()]:" << "error: only " << ifs.gcount() << " could be read";
        }
        ifs.close();
    } else {
        std::cerr << "[" << __FUNCTION__ << "()]:" << "can not open pubkey file: " << pubkeyFile << std::endl;
        abort();
    }
}

static bool startsWith(const std::string &s, const std::string &sub) {
    return s.find(sub) == 0;
}

static bool endsWith(const std::string &s, const std::string &sub) {
    return (s.rfind(sub) == (s.length() - sub.length())) && (s.length() >= sub.length());
}

static void populatePartitions(const char *path) {
    struct dirent *entry;
    DIR *dir = opendir(path);
    if (dir == nullptr) {
        std::cerr << "[" << __FUNCTION__ << "(path=" << path << ")]:";
        std::cerr << ": can not open dir: " << path << std::endl;
        return;
    }
    while ((entry = readdir(dir)) != nullptr) {
        if (entry->d_type == DT_REG) {
            auto dn = std::string(entry->d_name);
            if (endsWith(dn, ".img")) {
                validPartitions.insert(dn);
                //std::cout << "Valid: " << dn << std::endl;
            } else {
                //pass
            }
        } else {
            //pass
        }
    }
    closedir(dir);
    std::cout << "[" << __FUNCTION__ << "(path=" << path << ")]: parts = { ";
    for (const auto &validPartition : validPartitions) {
        std::cout << validPartition << " ";
    }
    std::cout << "}" << std::endl;
}

static bool fileExists(const std::string &name) {
    struct stat buffer;
    return (stat(name.c_str(), &buffer) == 0);
}

CfigAvbOps::CfigAvbOps() {
    memset(&avb_ops_, 0, sizeof(AvbOps));
    avb_ops_.get_size_of_partition = get_size_of_partitionX;
    avb_ops_.get_preloaded_partition = get_preloaded_partitionX;
    avb_ops_.get_unique_guid_for_partition = get_unique_guid_for_partitionX;

    avb_ops_.read_from_partition = read_from_partitionX;
    avb_ops_.read_is_device_unlocked = read_is_device_unlockedX;
    avb_ops_.read_rollback_index = read_rollback_indexX;
    avb_ops_.read_persistent_value = read_persistent_valueX;

    avb_ops_.write_to_partition = write_to_partitionX;
    avb_ops_.write_rollback_index = write_rollback_indexX;
    avb_ops_.write_persistent_value = write_persistent_valueX;

    avb_ops_.validate_vbmeta_public_key = validate_vbmeta_public_keyX;
    avb_ops_.validate_public_key_for_partition = validate_public_key_for_partitionX;

    avb_ops_.user_data = this;

    loadPubkey();
    populatePartitions(".");
    if (!fileExists("config")) {
        std::cout << "init: config/ dir" << std::endl;
        if (-1 == mkdir("config", S_IRWXU | S_IRWXG | S_IROTH | S_IXOTH)) {
            std::cout << "can not make config dir: " << errno << ", msg=" << strerror(errno) << std::endl;
        }
    }
    if (!fileExists(lockStatusFile)) {
        std::cout << __FUNCTION__ << ": lockStatusFile" << std::endl;
        write_to_file(lockStatusFile, "");
    }
    if (!fileExists("config/rollbackIndex_0")) {
        std::cout << "config/rollbackIndex_0" << std::endl;
        write_to_file("config/rollbackIndex_0", "0");
    }
    if (!fileExists("config/rollbackIndex_1")) {
        std::cout << "config/rollbackIndex_1" << std::endl;
        write_to_file("config/rollbackIndex_1", "0");
    }
}
