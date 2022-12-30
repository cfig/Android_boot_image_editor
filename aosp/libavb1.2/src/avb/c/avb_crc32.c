/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* See https://en.wikipedia.org/wiki/Computation_of_cyclic_redundancy_checks
 * for info on the general algorithm. We use the following configuration:
 *   32-bit CRC
 *   Polynomial = 0x04C11DB7
 *   MSB-first
 *   Input and output complement
 *   Input and output bit reversal
 *
 * This implementation optimizes for size and readability. We only need this for
 * 28 bytes of A/B booting metadata, so efficiency is largely irrelevant whereas
 * a 1KiB lookup table can be a significant cost for bootloaders.
 */

#include "avb_util.h"

/* Lookup table for reversing 4 bits. */
/* clang-format off */
static uint8_t reverse_4bit_table[] = {
  0x0, 0x8, 0x4, 0xC,
  0x2, 0xA, 0x6, 0xE,
  0x1, 0x9, 0x5, 0xD,
  0x3, 0xB, 0x7, 0xF
};
/* clang-format on */

static uint8_t reverse_byte(uint8_t val) {
  return (reverse_4bit_table[val & 0xF] << 4) | reverse_4bit_table[val >> 4];
}

static uint32_t reverse_uint32(uint32_t val) {
  return (reverse_byte(val) << 24) | (reverse_byte(val >> 8) << 16) |
         (reverse_byte(val >> 16) << 8) | reverse_byte(val >> 24);
}

uint32_t avb_crc32(const uint8_t* buf, size_t size) {
  uint32_t crc = 0xFFFFFFFF;

  for (size_t i = 0; i < size; ++i) {
    crc = crc ^ ((uint32_t)reverse_byte(buf[i]) << 24);
    for (int j = 0; j < 8; ++j) {
      if (crc & 0x80000000) {
        crc = (crc << 1) ^ 0x04C11DB7;
      } else {
        crc <<= 1;
      }
    }
  }

  return reverse_uint32(~crc);
}
