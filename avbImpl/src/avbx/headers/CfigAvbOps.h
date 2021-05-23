/*
 * Copyright 2021 yuyezhong@gmail.com
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
