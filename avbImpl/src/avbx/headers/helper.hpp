// Copyright 2021 yuyezhong@gmail.com
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

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
