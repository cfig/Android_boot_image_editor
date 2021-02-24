/*
 * main.cpp
 * Copyright (C) 2020 yu <yu@X.local>
 *
 * Distributed under terms of the MIT license.
 */

#include <cstdio>
#include <iostream>
#include <fstream>
#include <cstdlib>
#include <vector>
#include <regex>
#include "CfigAvbOps.h"
#include "helper.hpp"

std::vector<std::string> splitString(const std::string& subject) {
    if (subject.size() == 0) {
        return std::vector<std::string>();
    }
    static const std::regex re{"\\s+"};
    std::vector<std::string> container {
        std::sregex_token_iterator(subject.begin(), subject.end(), re, -1),
        std::sregex_token_iterator()
    };
    return container;
}

int main(int, char**) {
    int flags = AVB_SLOT_VERIFY_FLAGS_NONE;
    //param 1: preload partitions
    auto preloads = getenv("preloads");
    if (preloads == NULL) {
        preloads = (char*) "";
    }
    std::cout << "[" << __FUNCTION__ << "]: partitions to preload: " << preloads << std::endl;

    //param 2: requested partitions
    auto requests = getenv("requests");
    if (requests == NULL) {
        requests = (char*) "";
    }
    std::cout << "[" << __FUNCTION__ << "]: requested partitions: " << requests << std::endl;
    auto requestVec = splitString(requests);
    //example: const char* requestedPartitions[] = { (const char*) "boot", (const char*)NULL };
    const char* requestedPartitions[requestVec.size() + 1];
    for (int i = 0; i< requestVec.size(); i++) {
        requestedPartitions[i] = requestVec[i].c_str();
        if (requestVec[i] == "recovery") {
            flags |= AVB_SLOT_VERIFY_FLAGS_NO_VBMETA_PARTITION;
            std::cout << "[" << __FUNCTION__ << "]: using NO-VBMETA mode for recovery" << std::endl;
        }
    }
    requestedPartitions[requestVec.size()] = (const char*)NULL;
    //param3: ab_suffix
    auto abSuffix = getenv("suffix");
    if (abSuffix == NULL) {
        abSuffix = (char*) "";
    }
    std::cout << "[" << __FUNCTION__ << "]: ab_suffix: " << abSuffix << std::endl;

    //main
    auto cfigOps = CfigAvbOps();

    {//preload partitions
        auto preloadVec = splitString(preloads);
        for (auto item: preloadVec) {
            cfigOps.preload_partition(item);
        }
    }

    bool isDeviceUnlocked = false;
    cfigOps.avb_ops_.read_is_device_unlocked(NULL, &isDeviceUnlocked);
    if (isDeviceUnlocked) {
        flags |= AVB_SLOT_VERIFY_FLAGS_ALLOW_VERIFICATION_ERROR;
    }
    std::cout << "[" << __FUNCTION__ << "]: flags: " << flags << std::endl;

    AvbSlotVerifyData *slotData = NULL;
    AvbSlotVerifyResult result = avb_slot_verify(
            &(cfigOps.avb_ops_),
            requestedPartitions,
            abSuffix, /* ab_suffix */
            static_cast<AvbSlotVerifyFlags>(flags),    /* AvbSlotVerifyFlags */
            AVB_HASHTREE_ERROR_MODE_RESTART_AND_INVALIDATE,
            &slotData);
    if (AVB_SLOT_VERIFY_RESULT_OK == result) {
        auto outFile = "verify_result.json";
        std::cout << "Writing result to " << outFile << "... ";
        std::ofstream outJson(outFile);
        outJson << toString(slotData);
        outJson.close();
        std::cout << " done" << std::endl;
        std::cout << "Run:\n  python -m json.tool " << outFile << std::endl;
    }
    if (slotData) { avb_slot_verify_data_free(slotData); }
    std::cout << "\n\tVerify Result: " << toString(result) << std::endl;
    if (isDeviceUnlocked) {
        switch (result) {
            case AVB_SLOT_VERIFY_RESULT_OK:
                std::cout << "\tVerify Flow: [orange] continue";
                break;
            case AVB_SLOT_VERIFY_RESULT_ERROR_VERIFICATION:
            case AVB_SLOT_VERIFY_RESULT_ERROR_PUBLIC_KEY_REJECTED:
            case AVB_SLOT_VERIFY_RESULT_ERROR_ROLLBACK_INDEX:
                std::cout << "\tVerify Flow: [orange] allowed errors found: " << toString(result) << std::endl;
                break;
            default:
                std::cout<< "\tVerify Flow: [orange] but fatal errors found" << std::endl;
        }
    } else {
        switch (result) {
            case AVB_SLOT_VERIFY_RESULT_OK:
                std::cout << "\tVerify Flow: [green] continue";
                break;
            default:
                std::cout << "\tVerify Flow: [?????] halt";
        }
    }
    return 0;
}
