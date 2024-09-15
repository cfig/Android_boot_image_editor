/*
 * Copyright (C) 2013 The Android Open Source Project
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

#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include <sparse/sparse.h>
#include "backed_block.h"
#include "sparse_file.h"

#ifndef O_BINARY
#define O_BINARY 0
#endif

#if defined(__APPLE__) && defined(__MACH__)
#define lseek64 lseek
#endif
#if defined(__APPLE__) && defined(__MACH__)
#define lseek64 lseek
#define off64_t off_t
#endif

void usage() {
  fprintf(stderr, "Usage: append2simg <output> <input>\n");
}

int main(int argc, char* argv[]) {
  int output;
  int output_block;
  char* output_path;
  struct sparse_file* sparse_output;

  int input;
  char* input_path;
  off64_t input_len;

  int tmp_fd;
  char* tmp_path;

  int ret;

  if (argc == 3) {
    output_path = argv[1];
    input_path = argv[2];
  } else {
    usage();
    exit(-1);
  }

  ret = asprintf(&tmp_path, "%s.append2simg", output_path);
  if (ret < 0) {
    fprintf(stderr, "Couldn't allocate filename\n");
    exit(-1);
  }

  output = open(output_path, O_RDWR | O_BINARY);
  if (output < 0) {
    fprintf(stderr, "Couldn't open output file (%s)\n", strerror(errno));
    exit(-1);
  }

  sparse_output = sparse_file_import_auto(output, false, true);
  if (!sparse_output) {
    fprintf(stderr, "Couldn't import output file\n");
    exit(-1);
  }

  input = open(input_path, O_RDONLY | O_BINARY);
  if (input < 0) {
    fprintf(stderr, "Couldn't open input file (%s)\n", strerror(errno));
    exit(-1);
  }

  input_len = lseek64(input, 0, SEEK_END);
  if (input_len < 0) {
    fprintf(stderr, "Couldn't get input file length (%s)\n", strerror(errno));
    exit(-1);
  } else if (input_len % sparse_output->block_size) {
    fprintf(stderr, "Input file is not a multiple of the output file's block size");
    exit(-1);
  }
  lseek64(input, 0, SEEK_SET);

  output_block = sparse_output->len / sparse_output->block_size;
  if (sparse_file_add_fd(sparse_output, input, 0, input_len, output_block) < 0) {
    fprintf(stderr, "Couldn't add input file\n");
    exit(-1);
  }
  sparse_output->len += input_len;

  tmp_fd = open(tmp_path, O_WRONLY | O_CREAT | O_BINARY, 0664);
  if (tmp_fd < 0) {
    fprintf(stderr, "Couldn't open temporary file (%s)\n", strerror(errno));
    exit(-1);
  }

  lseek64(output, 0, SEEK_SET);
  if (sparse_file_write(sparse_output, tmp_fd, false, true, false) < 0) {
    fprintf(stderr, "Failed to write sparse file\n");
    exit(-1);
  }

  sparse_file_destroy(sparse_output);
  close(tmp_fd);
  close(output);
  close(input);

  ret = rename(tmp_path, output_path);
  if (ret < 0) {
    fprintf(stderr, "Failed to rename temporary file (%s)\n", strerror(errno));
    exit(-1);
  }

  free(tmp_path);

  exit(0);
}
