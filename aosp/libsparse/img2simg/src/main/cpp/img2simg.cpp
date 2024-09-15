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
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

#include <sparse/sparse.h>

#ifndef O_BINARY
#define O_BINARY 0
#endif

#if defined(__APPLE__) && defined(__MACH__)
#define lseek64 lseek
#define off64_t off_t
#endif

void usage() {
  fprintf(stderr, "Usage: img2simg <raw_image_file> <sparse_image_file> [<block_size>]\n");
}

int main(int argc, char* argv[]) {
  int in;
  int out;
  int ret;
  struct sparse_file* s;
  unsigned int block_size = 4096;
  off64_t len;

  if (argc < 3 || argc > 4) {
    usage();
    exit(-1);
  }

  if (argc == 4) {
    block_size = atoi(argv[3]);
  }

  if (block_size < 1024 || block_size % 4 != 0) {
    usage();
    exit(-1);
  }

  if (strcmp(argv[1], "-") == 0) {
    in = STDIN_FILENO;
  } else {
    in = open(argv[1], O_RDONLY | O_BINARY);
    if (in < 0) {
      fprintf(stderr, "Cannot open input file %s\n", argv[1]);
      exit(-1);
    }
  }

  if (strcmp(argv[2], "-") == 0) {
    out = STDOUT_FILENO;
  } else {
    out = open(argv[2], O_WRONLY | O_CREAT | O_TRUNC | O_BINARY, 0664);
    if (out < 0) {
      fprintf(stderr, "Cannot open output file %s\n", argv[2]);
      exit(-1);
    }
  }

  len = lseek64(in, 0, SEEK_END);
  lseek64(in, 0, SEEK_SET);

  s = sparse_file_new(block_size, len);
  if (!s) {
    fprintf(stderr, "Failed to create sparse file\n");
    exit(-1);
  }

  sparse_file_verbose(s);
  ret = sparse_file_read(s, in, false, false);
  if (ret) {
    fprintf(stderr, "Failed to read file\n");
    exit(-1);
  }

  ret = sparse_file_write(s, out, false, true, false);
  if (ret) {
    fprintf(stderr, "Failed to write sparse file\n");
    exit(-1);
  }

  close(in);
  close(out);

  exit(0);
}
