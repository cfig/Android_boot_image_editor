/*
 * helper.h
 * Copyright (C) 2020 yu <yu@X.local>
 *
 * Distributed under terms of the MIT license.
 */

#ifndef HELPER_H
#define HELPER_H

#include <string>
#include <libavb.h>

std::string toString(const AvbSlotVerifyData* slotData);
std::string toString(const AvbVBMetaData* vbmetaData);
std::string toString(const AvbPartitionData* partitionData);
std::string toString(const uint8_t* ba, int baSize);
std::string toString(/* enum */ AvbHashtreeErrorMode errorMode);
std::string toString(/* enum */ AvbSlotVerifyResult slotVerifyResult);
std::string toString(/* enum */ AvbVBMetaVerifyResult metaVerifyResult);

#endif /* !HELPER_H */
