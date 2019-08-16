/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define _FILE_OFFSET_BITS 64
#define _LARGEFILE64_SOURCE 1

#include <fcntl.h>
#include <inttypes.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <algorithm>
#include <string>

#include <sparse/sparse.h>

#include "android-base/stringprintf.h"
#include "defs.h"
#include "output_file.h"
#include "sparse_crc32.h"
#include "sparse_file.h"
#include "sparse_format.h"

#if defined(__APPLE__) && defined(__MACH__)
#define lseek64 lseek
#define off64_t off_t
#endif

#define SPARSE_HEADER_MAJOR_VER 1
#define SPARSE_HEADER_LEN (sizeof(sparse_header_t))
#define CHUNK_HEADER_LEN (sizeof(chunk_header_t))

static constexpr int64_t COPY_BUF_SIZE = 1024 * 1024;
static char* copybuf;

static std::string ErrorString(int err) {
  if (err == -EOVERFLOW) return "EOF while reading file";
  if (err == -EINVAL) return "Invalid sparse file format";
  if (err == -ENOMEM) return "Failed allocation while reading file";
  return android::base::StringPrintf("Unknown error %d", err);
}

class SparseFileSource {
 public:
  /* Seeks the source ahead by the given offset. */
  virtual void Seek(int64_t offset) = 0;

  /* Return the current offset. */
  virtual int64_t GetOffset() = 0;

  /* Set the current offset. Return 0 if successful. */
  virtual int SetOffset(int64_t offset) = 0;

  /* Adds the given length from the current offset of the source to the file at the given block.
   * Return 0 if successful. */
  virtual int AddToSparseFile(struct sparse_file* s, int64_t len, unsigned int block) = 0;

  /* Get data of fixed size from the current offset and seek len bytes. Return 0 if successful. */
  virtual int ReadValue(void* ptr, int len) = 0;

  /* Find the crc32 of the next len bytes and seek ahead len bytes. Return 0 if successful. */
  virtual int GetCrc32(uint32_t* crc32, int64_t len) = 0;

  virtual ~SparseFileSource(){};
};

class SparseFileFdSource : public SparseFileSource {
 private:
  int fd;

 public:
  SparseFileFdSource(int fd) : fd(fd) {}
  ~SparseFileFdSource() override {}

  void Seek(int64_t off) override { lseek64(fd, off, SEEK_CUR); }

  int64_t GetOffset() override { return lseek64(fd, 0, SEEK_CUR); }

  int SetOffset(int64_t offset) override {
    return lseek64(fd, offset, SEEK_SET) == offset ? 0 : -errno;
  }

  int AddToSparseFile(struct sparse_file* s, int64_t len, unsigned int block) override {
    return sparse_file_add_fd(s, fd, GetOffset(), len, block);
  }

  int ReadValue(void* ptr, int len) override { return read_all(fd, ptr, len); }

  int GetCrc32(uint32_t* crc32, int64_t len) override {
    int chunk;
    int ret;
    while (len) {
      chunk = std::min(len, COPY_BUF_SIZE);
      ret = read_all(fd, copybuf, chunk);
      if (ret < 0) {
        return ret;
      }
      *crc32 = sparse_crc32(*crc32, copybuf, chunk);
      len -= chunk;
    }
    return 0;
  }
};

class SparseFileBufSource : public SparseFileSource {
 private:
  char* buf;
  int64_t offset;

 public:
  SparseFileBufSource(char* buf) : buf(buf), offset(0) {}
  ~SparseFileBufSource() override {}

  void Seek(int64_t off) override {
    buf += off;
    offset += off;
  }

  int64_t GetOffset() override { return offset; }

  int SetOffset(int64_t off) override {
    buf += off - offset;
    offset = off;
    return 0;
  }

  int AddToSparseFile(struct sparse_file* s, int64_t len, unsigned int block) override {
    return sparse_file_add_data(s, buf, len, block);
  }

  int ReadValue(void* ptr, int len) override {
    memcpy(ptr, buf, len);
    Seek(len);
    return 0;
  }

  int GetCrc32(uint32_t* crc32, int64_t len) override {
    *crc32 = sparse_crc32(*crc32, buf, len);
    Seek(len);
    return 0;
  }
};

static void verbose_error(bool verbose, int err, const char* fmt, ...) {
  if (!verbose) return;

  std::string msg = ErrorString(err);
  if (fmt) {
    msg += " at ";
    va_list argp;
    va_start(argp, fmt);
    android::base::StringAppendV(&msg, fmt, argp);
    va_end(argp);
  }
  sparse_print_verbose("%s\n", msg.c_str());
}

static int process_raw_chunk(struct sparse_file* s, unsigned int chunk_size,
                             SparseFileSource* source, unsigned int blocks, unsigned int block,
                             uint32_t* crc32) {
  int ret;
  int64_t len = blocks * s->block_size;

  if (chunk_size % s->block_size != 0) {
    return -EINVAL;
  }

  if (chunk_size / s->block_size != blocks) {
    return -EINVAL;
  }

  ret = source->AddToSparseFile(s, len, block);
  if (ret < 0) {
    return ret;
  }

  if (crc32) {
    ret = source->GetCrc32(crc32, len);
    if (ret < 0) {
      return ret;
    }
  } else {
    source->Seek(len);
  }

  return 0;
}

static int process_fill_chunk(struct sparse_file* s, unsigned int chunk_size,
                              SparseFileSource* source, unsigned int blocks, unsigned int block,
                              uint32_t* crc32) {
  int ret;
  int chunk;
  int64_t len = (int64_t)blocks * s->block_size;
  uint32_t fill_val;
  uint32_t* fillbuf;
  unsigned int i;

  if (chunk_size != sizeof(fill_val)) {
    return -EINVAL;
  }

  ret = source->ReadValue(&fill_val, sizeof(fill_val));
  if (ret < 0) {
    return ret;
  }

  ret = sparse_file_add_fill(s, fill_val, len, block);
  if (ret < 0) {
    return ret;
  }

  if (crc32) {
    /* Fill copy_buf with the fill value */
    fillbuf = (uint32_t*)copybuf;
    for (i = 0; i < (COPY_BUF_SIZE / sizeof(fill_val)); i++) {
      fillbuf[i] = fill_val;
    }

    while (len) {
      chunk = std::min(len, COPY_BUF_SIZE);
      *crc32 = sparse_crc32(*crc32, copybuf, chunk);
      len -= chunk;
    }
  }

  return 0;
}

static int process_skip_chunk(struct sparse_file* s, unsigned int chunk_size,
                              SparseFileSource* source __unused, unsigned int blocks,
                              unsigned int block __unused, uint32_t* crc32) {
  if (chunk_size != 0) {
    return -EINVAL;
  }

  if (crc32) {
    int64_t len = (int64_t)blocks * s->block_size;
    memset(copybuf, 0, COPY_BUF_SIZE);

    while (len) {
      int chunk = std::min(len, COPY_BUF_SIZE);
      *crc32 = sparse_crc32(*crc32, copybuf, chunk);
      len -= chunk;
    }
  }

  return 0;
}

static int process_crc32_chunk(SparseFileSource* source, unsigned int chunk_size, uint32_t* crc32) {
  uint32_t file_crc32;

  if (chunk_size != sizeof(file_crc32)) {
    return -EINVAL;
  }

  int ret = source->ReadValue(&file_crc32, sizeof(file_crc32));
  if (ret < 0) {
    return ret;
  }

  if (crc32 != nullptr && file_crc32 != *crc32) {
    return -EINVAL;
  }

  return 0;
}

static int process_chunk(struct sparse_file* s, SparseFileSource* source, unsigned int chunk_hdr_sz,
                         chunk_header_t* chunk_header, unsigned int cur_block, uint32_t* crc_ptr) {
  int ret;
  unsigned int chunk_data_size;
  int64_t offset = source->GetOffset();

  chunk_data_size = chunk_header->total_sz - chunk_hdr_sz;

  switch (chunk_header->chunk_type) {
    case CHUNK_TYPE_RAW:
      ret =
          process_raw_chunk(s, chunk_data_size, source, chunk_header->chunk_sz, cur_block, crc_ptr);
      if (ret < 0) {
        verbose_error(s->verbose, ret, "data block at %" PRId64, offset);
        return ret;
      }
      return chunk_header->chunk_sz;
    case CHUNK_TYPE_FILL:
      ret = process_fill_chunk(s, chunk_data_size, source, chunk_header->chunk_sz, cur_block,
                               crc_ptr);
      if (ret < 0) {
        verbose_error(s->verbose, ret, "fill block at %" PRId64, offset);
        return ret;
      }
      return chunk_header->chunk_sz;
    case CHUNK_TYPE_DONT_CARE:
      ret = process_skip_chunk(s, chunk_data_size, source, chunk_header->chunk_sz, cur_block,
                               crc_ptr);
      if (chunk_data_size != 0) {
        if (ret < 0) {
          verbose_error(s->verbose, ret, "skip block at %" PRId64, offset);
          return ret;
        }
      }
      return chunk_header->chunk_sz;
    case CHUNK_TYPE_CRC32:
      ret = process_crc32_chunk(source, chunk_data_size, crc_ptr);
      if (ret < 0) {
        verbose_error(s->verbose, -EINVAL, "crc block at %" PRId64, offset);
        return ret;
      }
      return 0;
    default:
      verbose_error(s->verbose, -EINVAL, "unknown block %04X at %" PRId64, chunk_header->chunk_type,
                    offset);
  }

  return 0;
}

static int sparse_file_read_sparse(struct sparse_file* s, SparseFileSource* source, bool crc) {
  int ret;
  unsigned int i;
  sparse_header_t sparse_header;
  chunk_header_t chunk_header;
  uint32_t crc32 = 0;
  uint32_t* crc_ptr = nullptr;
  unsigned int cur_block = 0;

  if (!copybuf) {
    copybuf = (char*)malloc(COPY_BUF_SIZE);
  }

  if (!copybuf) {
    return -ENOMEM;
  }

  if (crc) {
    crc_ptr = &crc32;
  }

  ret = source->ReadValue(&sparse_header, sizeof(sparse_header));
  if (ret < 0) {
    return ret;
  }

  if (sparse_header.magic != SPARSE_HEADER_MAGIC) {
    return -EINVAL;
  }

  if (sparse_header.major_version != SPARSE_HEADER_MAJOR_VER) {
    return -EINVAL;
  }

  if (sparse_header.file_hdr_sz < SPARSE_HEADER_LEN) {
    return -EINVAL;
  }

  if (sparse_header.chunk_hdr_sz < sizeof(chunk_header)) {
    return -EINVAL;
  }

  if (sparse_header.file_hdr_sz > SPARSE_HEADER_LEN) {
    /* Skip the remaining bytes in a header that is longer than
     * we expected.
     */
    source->Seek(sparse_header.file_hdr_sz - SPARSE_HEADER_LEN);
  }

  for (i = 0; i < sparse_header.total_chunks; i++) {
    ret = source->ReadValue(&chunk_header, sizeof(chunk_header));
    if (ret < 0) {
      return ret;
    }

    if (sparse_header.chunk_hdr_sz > CHUNK_HEADER_LEN) {
      /* Skip the remaining bytes in a header that is longer than
       * we expected.
       */
      source->Seek(sparse_header.chunk_hdr_sz - CHUNK_HEADER_LEN);
    }

    ret = process_chunk(s, source, sparse_header.chunk_hdr_sz, &chunk_header, cur_block, crc_ptr);
    if (ret < 0) {
      return ret;
    }

    cur_block += ret;
  }

  if (sparse_header.total_blks != cur_block) {
    return -EINVAL;
  }

  return 0;
}

static int sparse_file_read_normal(struct sparse_file* s, int fd) {
  int ret;
  uint32_t* buf = (uint32_t*)malloc(s->block_size);
  unsigned int block = 0;
  int64_t remain = s->len;
  int64_t offset = 0;
  unsigned int to_read;
  unsigned int i;
  bool sparse_block;

  if (!buf) {
    return -ENOMEM;
  }

  while (remain > 0) {
    to_read = std::min(remain, (int64_t)(s->block_size));
    ret = read_all(fd, buf, to_read);
    if (ret < 0) {
      error("failed to read sparse file");
      free(buf);
      return ret;
    }

    if (to_read == s->block_size) {
      sparse_block = true;
      for (i = 1; i < s->block_size / sizeof(uint32_t); i++) {
        if (buf[0] != buf[i]) {
          sparse_block = false;
          break;
        }
      }
    } else {
      sparse_block = false;
    }

    if (sparse_block) {
      /* TODO: add flag to use skip instead of fill for buf[0] == 0 */
      sparse_file_add_fill(s, buf[0], to_read, block);
    } else {
      sparse_file_add_fd(s, fd, offset, to_read, block);
    }

    remain -= to_read;
    offset += to_read;
    block++;
  }

  free(buf);
  return 0;
}

int sparse_file_read(struct sparse_file* s, int fd, bool sparse, bool crc) {
  if (crc && !sparse) {
    return -EINVAL;
  }

  if (sparse) {
    SparseFileFdSource source(fd);
    return sparse_file_read_sparse(s, &source, crc);
  } else {
    return sparse_file_read_normal(s, fd);
  }
}

int sparse_file_read_buf(struct sparse_file* s, char* buf, bool crc) {
  SparseFileBufSource source(buf);
  return sparse_file_read_sparse(s, &source, crc);
}

static struct sparse_file* sparse_file_import_source(SparseFileSource* source, bool verbose,
                                                     bool crc) {
  int ret;
  sparse_header_t sparse_header;
  int64_t len;
  struct sparse_file* s;

  ret = source->ReadValue(&sparse_header, sizeof(sparse_header));
  if (ret < 0) {
    verbose_error(verbose, ret, "header");
    return nullptr;
  }

  if (sparse_header.magic != SPARSE_HEADER_MAGIC) {
    verbose_error(verbose, -EINVAL, "header magic");
    return nullptr;
  }

  if (sparse_header.major_version != SPARSE_HEADER_MAJOR_VER) {
    verbose_error(verbose, -EINVAL, "header major version");
    return nullptr;
  }

  if (sparse_header.file_hdr_sz < SPARSE_HEADER_LEN) {
    return nullptr;
  }

  if (sparse_header.chunk_hdr_sz < sizeof(chunk_header_t)) {
    return nullptr;
  }

  len = (int64_t)sparse_header.total_blks * sparse_header.blk_sz;
  s = sparse_file_new(sparse_header.blk_sz, len);
  if (!s) {
    verbose_error(verbose, -EINVAL, nullptr);
    return nullptr;
  }

  ret = source->SetOffset(0);
  if (ret < 0) {
    verbose_error(verbose, ret, "seeking");
    sparse_file_destroy(s);
    return nullptr;
  }

  s->verbose = verbose;

  ret = sparse_file_read_sparse(s, source, crc);
  if (ret < 0) {
    sparse_file_destroy(s);
    return nullptr;
  }

  return s;
}

struct sparse_file* sparse_file_import(int fd, bool verbose, bool crc) {
  SparseFileFdSource source(fd);
  return sparse_file_import_source(&source, verbose, crc);
}

struct sparse_file* sparse_file_import_buf(char* buf, bool verbose, bool crc) {
  SparseFileBufSource source(buf);
  return sparse_file_import_source(&source, verbose, crc);
}

struct sparse_file* sparse_file_import_auto(int fd, bool crc, bool verbose) {
  struct sparse_file* s;
  int64_t len;
  int ret;

  s = sparse_file_import(fd, verbose, crc);
  if (s) {
    return s;
  }

  len = lseek64(fd, 0, SEEK_END);
  if (len < 0) {
    return nullptr;
  }

  lseek64(fd, 0, SEEK_SET);

  s = sparse_file_new(4096, len);
  if (!s) {
    return nullptr;
  }

  ret = sparse_file_read_normal(s, fd);
  if (ret < 0) {
    sparse_file_destroy(s);
    return nullptr;
  }

  return s;
}
