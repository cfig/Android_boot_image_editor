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

void usage() {
  fprintf(stderr, "Usage: simg2simg <sparse image file> <sparse_image_file> <max_size>\n");
}

int main(int argc, char* argv[]) {
  int in;
  int out;
  int i;
  int ret;
  struct sparse_file* s;
  int64_t max_size;
  struct sparse_file** out_s;
  int files;
  char filename[4096];

  if (argc != 4) {
    usage();
    exit(-1);
  }

  max_size = atoll(argv[3]);

  in = open(argv[1], O_RDONLY | O_BINARY);
  if (in < 0) {
    fprintf(stderr, "Cannot open input file %s\n", argv[1]);
    exit(-1);
  }

  s = sparse_file_import(in, true, false);
  if (!s) {
    fprintf(stderr, "Failed to import sparse file\n");
    exit(-1);
  }

  files = sparse_file_resparse(s, max_size, nullptr, 0);
  if (files < 0) {
    fprintf(stderr, "Failed to resparse\n");
    exit(-1);
  }

  out_s = (sparse_file**) calloc(sizeof(struct sparse_file*), files);
  if (!out_s) {
    fprintf(stderr, "Failed to allocate sparse file array\n");
    exit(-1);
  }

  files = sparse_file_resparse(s, max_size, out_s, files);
  if (files < 0) {
    fprintf(stderr, "Failed to resparse\n");
    exit(-1);
  }

  for (i = 0; i < files; i++) {
    ret = snprintf(filename, sizeof(filename), "%s.%d", argv[2], i);
    if (ret >= (int)sizeof(filename)) {
      fprintf(stderr, "Filename too long\n");
      exit(-1);
    }

    out = open(filename, O_WRONLY | O_CREAT | O_TRUNC | O_BINARY, 0664);
    if (out < 0) {
      fprintf(stderr, "Cannot open output file %s\n", argv[2]);
      exit(-1);
    }

    ret = sparse_file_write(out_s[i], out, false, true, false);
    if (ret) {
      fprintf(stderr, "Failed to write sparse file\n");
      exit(-1);
    }
    close(out);
  }

  close(in);

  exit(0);
}
