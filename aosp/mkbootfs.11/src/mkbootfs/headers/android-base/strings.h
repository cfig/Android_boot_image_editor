#pragma once

#include <sstream>
#include <string>
#include <vector>

namespace android {
namespace base {

// Tests whether 's' starts with 'prefix'.
bool StartsWith(std::string_view s, std::string_view prefix);
bool StartsWith(std::string_view s, char prefix);

// Tests whether 's' ends with 'suffix'.
bool EndsWith(std::string_view s, std::string_view suffix);
bool EndsWith(std::string_view s, char suffix);

}  // namespace base
}  // namespace android
