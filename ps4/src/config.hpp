#pragma once

#if defined(__has_include)
#  if __has_include("../config.local.hpp")
#    include "../config.local.hpp"
#  else
namespace ps4app::config {
inline constexpr char kSupabaseProjectUrl[] = "";
inline constexpr char kSupabasePublishableKey[] = "";
} // namespace ps4app::config
#  endif
#else
namespace ps4app::config {
inline constexpr char kSupabaseProjectUrl[] = "";
inline constexpr char kSupabasePublishableKey[] = "";
} // namespace ps4app::config
#endif
