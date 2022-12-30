/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 * BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
 * ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

#ifdef AVB_INSIDE_LIBAVB_H
#error "You can't include avb_crypto_ops_impl.h in the public header libavb.h."
#endif

#ifndef AVB_COMPILATION
#error "Never include this file, it may only be used from internal avb code."
#endif

#ifndef AVB_CRYPTO_OPS_IMPL_H_
#define AVB_CRYPTO_OPS_IMPL_H_

#ifdef __cplusplus
extern "C" {
#endif

#include "avb_sysdeps.h"

/* Block size in bytes of a SHA-256 digest. */
#define AVB_SHA256_BLOCK_SIZE 64

/* Block size in bytes of a SHA-512 digest. */
#define AVB_SHA512_BLOCK_SIZE 128

/* Data structure used for SHA-256. */
typedef struct {
  uint32_t h[8];
  uint64_t tot_len;
  size_t len;
  uint8_t block[2 * AVB_SHA256_BLOCK_SIZE];
} AvbSHA256ImplCtx;

/* Data structure used for SHA-512. */
typedef struct {
  uint64_t h[8];
  uint64_t tot_len;
  size_t len;
  uint8_t block[2 * AVB_SHA512_BLOCK_SIZE];
} AvbSHA512ImplCtx;

#define AVB_SHA256_CONTEXT_SIZE sizeof(AvbSHA256ImplCtx)
#define AVB_SHA512_CONTEXT_SIZE sizeof(AvbSHA512ImplCtx)

#ifdef __cplusplus
}
#endif

#endif /* AVB_CRYPTO_OPS_IMPL_H_ */
