#include "android-base/strings.h"
#include <string>
#include <vector>

namespace android {
namespace base {

bool StartsWith(std::string_view s, std::string_view prefix) {
  return s.substr(0, prefix.size()) == prefix;
}

bool StartsWith(std::string_view s, char prefix) {
  return !s.empty() && s.front() == prefix;
}


bool EndsWith(std::string_view s, std::string_view suffix) {
  return s.size() >= suffix.size() && s.substr(s.size() - suffix.size(), suffix.size()) == suffix;
}

bool EndsWith(std::string_view s, char suffix) {
  return !s.empty() && s.back() == suffix;
}

}  // namespace base
}  // namespace android
