#pragma once

#include <string>
#include <utility>
#include <vector>

namespace addonstation::json {

enum class Type {
    Null,
    Boolean,
    Number,
    String,
    Array,
    Object,
};

struct Value {
    Type type = Type::Null;
    bool boolean = false;
    // Used by String and Number. Number text is retained to avoid losing precision.
    std::string text;
    std::vector<Value> array;
    std::vector<std::pair<std::string, Value>> object;

    const Value* get(const std::string& key) const;
    std::string stringOr(const std::string& fallback = {}) const;
    std::string textOr(const std::string& fallback = {}) const;
    long long integerOr(long long fallback = 0) const;
    double numberOr(double fallback = 0.0) const;
    bool boolOr(bool fallback = false) const;
};

// Small, dependency-free JSON parser for Supabase REST responses. It accepts
// UTF-8 and decodes JSON \u escapes (including UTF-16 surrogate pairs).
bool parse(const std::string& input, Value& output, std::string* error = nullptr);

} // namespace addonstation::json
