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
    static const std::regex re{"\\s+"};
    std::vector<std::string> container{
        std::sregex_token_iterator(subject.begin(), subject.end(), re, -1), 
        std::sregex_token_iterator()
    };
    return container;
}

int main(int, char**) {
    auto cfigOps = CfigAvbOps();
    auto preloads = getenv("preload");
    if (preloads == NULL) {
    } else {
        auto preloadVec = splitString(preloads);
        for (auto item: preloadVec) {
            cfigOps.preload_partition(item);
        }
    }

    cfigOps.preload_partition("vbmeta");
    AvbSlotVerifyData *slotData = NULL;
    const char* requestedPartitions[] = { (const char*) "boot", (const char*)NULL };
    AvbSlotVerifyResult result = avb_slot_verify(
            &(cfigOps.avb_ops_),
            requestedPartitions,
            "",
            AVB_SLOT_VERIFY_FLAGS_NONE,
            AVB_HASHTREE_ERROR_MODE_RESTART_AND_INVALIDATE,
            &slotData);
    std::cout << "AvbSlotVerifyResult = " << toString(result) << std::endl;
    if (AVB_SLOT_VERIFY_RESULT_OK == result) {
        auto outFile = "verify_result.json";
        std::cout << "Writing result to " << outFile << "... ";
        std::ofstream outJson(outFile);
        outJson << toString(slotData);
        outJson.close();
        std::cout << " done" << std::endl;
    }
    return 0;
}
