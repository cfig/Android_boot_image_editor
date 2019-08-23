//
// Created by yu on 8/30/19.
//

#ifndef X1_CFIGAVBOPS_H
#define X1_CFIGAVBOPS_H

#include <map>
#include <libavb.h>

class CfigAvbOps {
public:
    CfigAvbOps();

    bool preload_partition(std::string partition);

    AvbOps avb_ops_{};
};

#endif //X1_CFIGAVBOPS_H
