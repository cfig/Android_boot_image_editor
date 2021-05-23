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

#include "helper.hpp"
#include <sstream>

std::string toString(const AvbSlotVerifyData* slotData) {
    if (!slotData) {
        return "{}";
    }
    std::stringstream ss;
    ss << "{";
    ss << "\"ab_suffix\":\"" << slotData->ab_suffix << "\",";
    ss << "\"num_vbmeta_images\":" <<  slotData->num_vbmeta_images << ",";

    ss << "\"vbmeta_images\":[";
    for (int i = 0; i < slotData->num_vbmeta_images; i++) {
        ss << toString(&((slotData->vbmeta_images)[i]));
        ss << ((i == slotData->num_vbmeta_images - 1) ? "" : ",");
    }
    ss << "],";

    ss << "\"num_loaded_partition\":\"" << slotData->num_loaded_partitions << "\",";

    ss << "\"loaded_partitions\":[";
    for (int i = 0; i < slotData->num_loaded_partitions; i++) {
        ss << toString(&((slotData->loaded_partitions)[i]));
        ss << ((i == slotData->num_loaded_partitions- 1) ? "" : ",");
    }
    ss << "],";

    ss << "\"cmdline\":\"" << slotData->cmdline << "\",";

    ss << "\"rollback_indexes\":[";
    for (int i = 0; i < AVB_MAX_NUMBER_OF_ROLLBACK_INDEX_LOCATIONS; i++) {
        ss << (slotData->rollback_indexes)[i];
        ss << ((i == AVB_MAX_NUMBER_OF_ROLLBACK_INDEX_LOCATIONS - 1) ? "" : ",");
    }
    ss << "],";

    ss << "\"resolved_hashtree_error_mode\":\"" << toString(slotData->resolved_hashtree_error_mode) << "\"";
    ss << "}";
    return ss.str();
}

std::string toString(AvbHashtreeErrorMode errorMode) {
    static const char* AvbHashtreeErrorMode_STRING[5] = {
        "AVB_HASHTREE_ERROR_MODE_RESTART_AND_INVALIDATE",
        "AVB_HASHTREE_ERROR_MODE_RESTART",
        "AVB_HASHTREE_ERROR_MODE_EIO",
        "AVB_HASHTREE_ERROR_MODE_LOGGING",
        "AVB_HASHTREE_ERROR_MODE_MANAGED_RESTART_AND_EIO",
    };
    return AvbHashtreeErrorMode_STRING[errorMode];
}

std::string toString(AvbSlotVerifyResult slotVerifyResult) {
    static const char* AvbSlotVerifyResult_STRING[9] = {
        "AVB_SLOT_VERIFY_RESULT_OK",
        "AVB_SLOT_VERIFY_RESULT_ERROR_OOM",
        "AVB_SLOT_VERIFY_RESULT_ERROR_IO",
        "AVB_SLOT_VERIFY_RESULT_ERROR_VERIFICATION",
        "AVB_SLOT_VERIFY_RESULT_ERROR_ROLLBACK_INDEX",
        "AVB_SLOT_VERIFY_RESULT_ERROR_PUBLIC_KEY_REJECTED",
        "AVB_SLOT_VERIFY_RESULT_ERROR_INVALID_METADATA",
        "AVB_SLOT_VERIFY_RESULT_ERROR_UNSUPPORTED_VERSION",
        "AVB_SLOT_VERIFY_RESULT_ERROR_INVALID_ARGUMENT",
    };
    return AvbSlotVerifyResult_STRING[slotVerifyResult];
}

std::string toString(const uint8_t* ba, int baSize) {
    //sb.append(Integer.toString((inData[i].toInt().and(0xff)) + 0x100, 16).substring(1))
    char byteStr[8] = { 0 };
    std::stringstream ss;
    for (int i = 0; i < baSize; i++) {
        sprintf(byteStr, "%02x", ba[i]);
        ss << byteStr;
    }
    return ss.str();
}

std::string toString(const AvbVBMetaData* vbmetaData) {
    std::stringstream ss;
    ss << "{";
    ss << "\"_type\":\"AvbVBMetaData\",";
    ss << "\"partition_name\":\"" << vbmetaData->partition_name << "\",";
    ss << "\"vbmeta_data\":\"" << toString((vbmetaData->vbmeta_data), vbmetaData->vbmeta_size) << "\",";
    ss << "\"vbmeta_size\":" << vbmetaData->vbmeta_size << ",";
    ss << "\"verify_result\":\"" << toString(vbmetaData->verify_result) << "\"";
    ss << "}";
    return ss.str();
}

std::string toString(/* enum */ AvbVBMetaVerifyResult metaVerifyResult) {
    static const char* AvbVBMetaVerifyResult_STRING[6] = {
        "AVB_VBMETA_VERIFY_RESULT_OK",
        "AVB_VBMETA_VERIFY_RESULT_OK_NOT_SIGNED",
        "AVB_VBMETA_VERIFY_RESULT_INVALID_VBMETA_HEADER",
        "AVB_VBMETA_VERIFY_RESULT_UNSUPPORTED_VERSION",
        "AVB_VBMETA_VERIFY_RESULT_HASH_MISMATCH",
        "AVB_VBMETA_VERIFY_RESULT_SIGNATURE_MISMATCH",
    };
    return AvbVBMetaVerifyResult_STRING[metaVerifyResult];
}

std::string toString(const AvbPartitionData* partitionData) {
    std::stringstream ss;
    ss << "{";
    ss << "\"partition_name\":\"" << partitionData->partition_name << "\",";
    //ss << "\"data\":\"" << toString(partitionData->data, partitionData->data_size) << "\",";
    ss << "\"data\":\"" << "omitted" << "\",";
    ss << "\"data_size\":\"" << partitionData->data_size << "\",";
    ss << "\"preloaded\":\"" << (partitionData->preloaded ? "true" : "false") << "\"";
    ss << "}";
    return ss.str();
}

