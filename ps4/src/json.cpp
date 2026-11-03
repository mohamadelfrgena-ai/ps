#include "json.hpp"

#include <cerrno>
#include <cstdlib>
#include <limits>

namespace addonstation::json {

const Value* Value::get(const std::string& key) const {
    if (type != Type::Object) return nullptr;
    for (const auto& entry : object) {
        if (entry.first == key) return &entry.second;
    }
    return nullptr;
}

std::string Value::stringOr(const std::string& fallback) const {
    return type == Type::String ? text : fallback;
}

std::string Value::textOr(const std::string& fallback) const {
    switch (type) {
        case Type::String:
        case Type::Number:
            return text;
        case Type::Boolean:
            return boolean ? "true" : "false";
        case Type::Null:
        case Type::Array:
        case Type::Object:
            return fallback;
    }
    return fallback;
}

long long Value::integerOr(long long fallback) const {
    if (type != Type::Number && type != Type::String) return fallback;
    if (text.empty()) return fallback;
    errno = 0;
    char* end = nullptr;
    const long long value = std::strtoll(text.c_str(), &end, 10);
    return errno == 0 && end != text.c_str() && *end == '\0' ? value : fallback;
}

double Value::numberOr(double fallback) const {
    if (type != Type::Number && type != Type::String) return fallback;
    if (text.empty()) return fallback;
    errno = 0;
    char* end = nullptr;
    const double value = std::strtod(text.c_str(), &end);
    return errno == 0 && end != text.c_str() && *end == '\0' ? value : fallback;
}

bool Value::boolOr(bool fallback) const {
    return type == Type::Boolean ? boolean : fallback;
}

namespace {

class Parser {
public:
    explicit Parser(const std::string& input) : input_(input) {}

    bool run(Value& output, std::string* error) {
        skipWhitespace();
        if (!parseValue(output, 0)) return finishError(error);
        skipWhitespace();
        if (position_ != input_.size()) {
            fail("unexpected characters after JSON value");
            return finishError(error);
        }
        if (error) error->clear();
        return true;
    }

private:
    static constexpr unsigned kMaxDepth = 32;
    const std::string& input_;
    std::size_t position_ = 0;
    std::string error_;

    bool finishError(std::string* error) const {
        if (error) *error = error_ + " at byte " + std::to_string(position_);
        return false;
    }

    void fail(const char* message) {
        if (error_.empty()) error_ = message;
    }

    void skipWhitespace() {
        while (position_ < input_.size()) {
            const char ch = input_[position_];
            if (ch != ' ' && ch != '\t' && ch != '\r' && ch != '\n') break;
            ++position_;
        }
    }

    bool consume(char expected) {
        if (position_ >= input_.size() || input_[position_] != expected) return false;
        ++position_;
        return true;
    }

    bool parseValue(Value& output, unsigned depth) {
        if (depth > kMaxDepth) {
            fail("JSON nesting limit exceeded");
            return false;
        }
        skipWhitespace();
        if (position_ >= input_.size()) {
            fail("unexpected end of input");
            return false;
        }

        switch (input_[position_]) {
            case 'n': return parseLiteral("null", Type::Null, output);
            case 't': return parseLiteral("true", Type::Boolean, output, true);
            case 'f': return parseLiteral("false", Type::Boolean, output, false);
            case '"':
                output = Value{};
                output.type = Type::String;
                return parseString(output.text);
            case '[': return parseArray(output, depth + 1);
            case '{': return parseObject(output, depth + 1);
            default:
                if (input_[position_] == '-' || isDigit(input_[position_]))
                    return parseNumber(output);
                fail("unexpected token");
                return false;
        }
    }

    bool parseLiteral(const char* literal, Type type, Value& output, bool boolean = false) {
        const std::string token(literal);
        if (input_.compare(position_, token.size(), token) != 0) {
            fail("invalid literal");
            return false;
        }
        position_ += token.size();
        output = Value{};
        output.type = type;
        output.boolean = boolean;
        return true;
    }

    bool parseArray(Value& output, unsigned depth) {
        consume('[');
        output = Value{};
        output.type = Type::Array;
        skipWhitespace();
        if (consume(']')) return true;

        while (position_ < input_.size()) {
            Value item;
            if (!parseValue(item, depth)) return false;
            output.array.push_back(std::move(item));
            skipWhitespace();
            if (consume(']')) return true;
            if (!consume(',')) {
                fail("expected ',' or ']' in array");
                return false;
            }
            skipWhitespace();
        }
        fail("unterminated array");
        return false;
    }

    bool parseObject(Value& output, unsigned depth) {
        consume('{');
        output = Value{};
        output.type = Type::Object;
        skipWhitespace();
        if (consume('}')) return true;

        while (position_ < input_.size()) {
            if (position_ >= input_.size() || input_[position_] != '"') {
                fail("expected string key in object");
                return false;
            }
            std::string key;
            if (!parseString(key)) return false;
            skipWhitespace();
            if (!consume(':')) {
                fail("expected ':' after object key");
                return false;
            }
            Value value;
            if (!parseValue(value, depth)) return false;
            output.object.emplace_back(std::move(key), std::move(value));
            skipWhitespace();
            if (consume('}')) return true;
            if (!consume(',')) {
                fail("expected ',' or '}' in object");
                return false;
            }
            skipWhitespace();
        }
        fail("unterminated object");
        return false;
    }

    static int hexValue(char ch) {
        if (ch >= '0' && ch <= '9') return ch - '0';
        if (ch >= 'a' && ch <= 'f') return ch - 'a' + 10;
        if (ch >= 'A' && ch <= 'F') return ch - 'A' + 10;
        return -1;
    }

    bool parseHexQuad(unsigned& value) {
        if (input_.size() - position_ < 4) {
            fail("incomplete Unicode escape");
            return false;
        }
        value = 0;
        for (unsigned i = 0; i < 4; ++i) {
            const int digit = hexValue(input_[position_++]);
            if (digit < 0) {
                fail("invalid Unicode escape");
                return false;
            }
            value = (value << 4) | static_cast<unsigned>(digit);
        }
        return true;
    }

    static void appendUtf8(std::string& output, unsigned codepoint) {
        if (codepoint <= 0x7F) {
            output.push_back(static_cast<char>(codepoint));
        } else if (codepoint <= 0x7FF) {
            output.push_back(static_cast<char>(0xC0 | (codepoint >> 6)));
            output.push_back(static_cast<char>(0x80 | (codepoint & 0x3F)));
        } else if (codepoint <= 0xFFFF) {
            output.push_back(static_cast<char>(0xE0 | (codepoint >> 12)));
            output.push_back(static_cast<char>(0x80 | ((codepoint >> 6) & 0x3F)));
            output.push_back(static_cast<char>(0x80 | (codepoint & 0x3F)));
        } else {
            output.push_back(static_cast<char>(0xF0 | (codepoint >> 18)));
            output.push_back(static_cast<char>(0x80 | ((codepoint >> 12) & 0x3F)));
            output.push_back(static_cast<char>(0x80 | ((codepoint >> 6) & 0x3F)));
            output.push_back(static_cast<char>(0x80 | (codepoint & 0x3F)));
        }
    }

    bool parseString(std::string& output) {
        if (!consume('"')) {
            fail("expected string");
            return false;
        }
        output.clear();
        while (position_ < input_.size()) {
            const unsigned char ch = static_cast<unsigned char>(input_[position_++]);
            if (ch == '"') return true;
            if (ch < 0x20) {
                fail("unescaped control character in string");
                return false;
            }
            if (ch != '\\') {
                output.push_back(static_cast<char>(ch));
                continue;
            }
            if (position_ >= input_.size()) {
                fail("unterminated escape sequence");
                return false;
            }
            const char escaped = input_[position_++];
            switch (escaped) {
                case '"': output.push_back('"'); break;
                case '\\': output.push_back('\\'); break;
                case '/': output.push_back('/'); break;
                case 'b': output.push_back('\b'); break;
                case 'f': output.push_back('\f'); break;
                case 'n': output.push_back('\n'); break;
                case 'r': output.push_back('\r'); break;
                case 't': output.push_back('\t'); break;
                case 'u': {
                    unsigned codepoint = 0;
                    if (!parseHexQuad(codepoint)) return false;
                    if (codepoint >= 0xD800 && codepoint <= 0xDBFF) {
                        if (input_.size() - position_ < 6 || input_[position_] != '\\' ||
                            input_[position_ + 1] != 'u') {
                            fail("missing low surrogate");
                            return false;
                        }
                        position_ += 2;
                        unsigned low = 0;
                        if (!parseHexQuad(low)) return false;
                        if (low < 0xDC00 || low > 0xDFFF) {
                            fail("invalid low surrogate");
                            return false;
                        }
                        codepoint = 0x10000 + ((codepoint - 0xD800) << 10) + (low - 0xDC00);
                    } else if (codepoint >= 0xDC00 && codepoint <= 0xDFFF) {
                        fail("unexpected low surrogate");
                        return false;
                    }
                    appendUtf8(output, codepoint);
                    break;
                }
                default:
                    fail("invalid escape sequence");
                    return false;
            }
        }
        fail("unterminated string");
        return false;
    }

    bool parseNumber(Value& output) {
        const std::size_t start = position_;
        consume('-');
        if (position_ >= input_.size()) {
            fail("incomplete number");
            return false;
        }
        if (consume('0')) {
            if (position_ < input_.size() && isDigit(input_[position_])) {
                fail("leading zero in number");
                return false;
            }
        } else {
            if (!isDigitOneToNine(input_[position_])) {
                fail("invalid number");
                return false;
            }
            while (position_ < input_.size() && isDigit(input_[position_])) ++position_;
        }
        if (consume('.')) {
            if (position_ >= input_.size() || !isDigit(input_[position_])) {
                fail("missing fraction digits");
                return false;
            }
            while (position_ < input_.size() && isDigit(input_[position_])) ++position_;
        }
        if (position_ < input_.size() && (input_[position_] == 'e' || input_[position_] == 'E')) {
            ++position_;
            if (position_ < input_.size() && (input_[position_] == '+' || input_[position_] == '-')) ++position_;
            if (position_ >= input_.size() || !isDigit(input_[position_])) {
                fail("missing exponent digits");
                return false;
            }
            while (position_ < input_.size() && isDigit(input_[position_])) ++position_;
        }
        output = Value{};
        output.type = Type::Number;
        output.text = input_.substr(start, position_ - start);
        return true;
    }

    static bool isDigit(char ch) { return ch >= '0' && ch <= '9'; }
    static bool isDigitOneToNine(char ch) { return ch >= '1' && ch <= '9'; }
};

} // namespace

bool parse(const std::string& input, Value& output, std::string* error) {
    Parser parser(input);
    return parser.run(output, error);
}

} // namespace addonstation::json
