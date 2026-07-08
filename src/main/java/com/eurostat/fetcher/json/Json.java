package com.eurostat.fetcher.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A minimal recursive-descent JSON parser covering exactly what's needed to read a
 * JSON-stat 2.0 dataset response: objects, arrays, strings, numbers, booleans and null.
 * No external dependency is available in this build environment, so this replaces Jackson.
 *
 * Objects parse to {@code LinkedHashMap<String,Object>}, arrays to {@code List<Object>},
 * numbers to {@code Double}, strings to {@code String}, booleans to {@code Boolean}, and
 * JSON null to Java {@code null}.
 */
public final class Json {

    private Json() {
    }

    public static Object parse(String text) throws JsonParseException {
        Parser parser = new Parser(text);
        parser.skipWhitespace();
        Object value = parser.parseValue();
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw new JsonParseException("Unexpected trailing content at offset " + parser.pos);
        }
        return value;
    }

    private static final class Parser {
        private final String text;
        private final int len;
        private int pos;

        Parser(String text) {
            this.text = text;
            this.len = text.length();
        }

        boolean atEnd() {
            return pos >= len;
        }

        void skipWhitespace() {
            while (pos < len) {
                char c = text.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        char peek() throws JsonParseException {
            if (pos >= len) {
                throw new JsonParseException("Unexpected end of input");
            }
            return text.charAt(pos);
        }

        void expect(char expected) throws JsonParseException {
            if (pos >= len || text.charAt(pos) != expected) {
                throw new JsonParseException("Expected '" + expected + "' at offset " + pos);
            }
            pos++;
        }

        Object parseValue() throws JsonParseException {
            skipWhitespace();
            char c = peek();
            switch (c) {
                case '{':
                    return parseObject();
                case '[':
                    return parseArray();
                case '"':
                    return parseString();
                case 't':
                case 'f':
                    return parseBoolean();
                case 'n':
                    return parseNull();
                default:
                    return parseNumber();
            }
        }

        Map<String, Object> parseObject() throws JsonParseException {
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            expect('{');
            skipWhitespace();
            if (!atEnd() && peek() == '}') {
                pos++;
                return result;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                Object value = parseValue();
                result.put(key, value);
                skipWhitespace();
                char c = peek();
                if (c == ',') {
                    pos++;
                } else if (c == '}') {
                    pos++;
                    break;
                } else {
                    throw new JsonParseException("Expected ',' or '}' at offset " + pos);
                }
            }
            return result;
        }

        List<Object> parseArray() throws JsonParseException {
            List<Object> result = new ArrayList<Object>();
            expect('[');
            skipWhitespace();
            if (!atEnd() && peek() == ']') {
                pos++;
                return result;
            }
            while (true) {
                skipWhitespace();
                result.add(parseValue());
                skipWhitespace();
                char c = peek();
                if (c == ',') {
                    pos++;
                } else if (c == ']') {
                    pos++;
                    break;
                } else {
                    throw new JsonParseException("Expected ',' or ']' at offset " + pos);
                }
            }
            return result;
        }

        String parseString() throws JsonParseException {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (atEnd()) {
                    throw new JsonParseException("Unterminated string");
                }
                char c = text.charAt(pos++);
                if (c == '"') {
                    break;
                }
                if (c == '\\') {
                    if (atEnd()) {
                        throw new JsonParseException("Unterminated escape sequence");
                    }
                    char esc = text.charAt(pos++);
                    switch (esc) {
                        case '"':
                            sb.append('"');
                            break;
                        case '\\':
                            sb.append('\\');
                            break;
                        case '/':
                            sb.append('/');
                            break;
                        case 'b':
                            sb.append('\b');
                            break;
                        case 'f':
                            sb.append('\f');
                            break;
                        case 'n':
                            sb.append('\n');
                            break;
                        case 'r':
                            sb.append('\r');
                            break;
                        case 't':
                            sb.append('\t');
                            break;
                        case 'u':
                            if (pos + 4 > len) {
                                throw new JsonParseException("Truncated unicode escape");
                            }
                            String hex = text.substring(pos, pos + 4);
                            pos += 4;
                            sb.append((char) Integer.parseInt(hex, 16));
                            break;
                        default:
                            throw new JsonParseException("Invalid escape '\\" + esc + "' at offset " + pos);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        Double parseNumber() throws JsonParseException {
            int start = pos;
            if (!atEnd() && text.charAt(pos) == '-') {
                pos++;
            }
            while (!atEnd() && Character.isDigit(text.charAt(pos))) {
                pos++;
            }
            if (!atEnd() && text.charAt(pos) == '.') {
                pos++;
                while (!atEnd() && Character.isDigit(text.charAt(pos))) {
                    pos++;
                }
            }
            if (!atEnd() && (text.charAt(pos) == 'e' || text.charAt(pos) == 'E')) {
                pos++;
                if (!atEnd() && (text.charAt(pos) == '+' || text.charAt(pos) == '-')) {
                    pos++;
                }
                while (!atEnd() && Character.isDigit(text.charAt(pos))) {
                    pos++;
                }
            }
            if (pos == start) {
                throw new JsonParseException("Invalid number at offset " + pos);
            }
            try {
                return Double.valueOf(text.substring(start, pos));
            } catch (NumberFormatException e) {
                throw new JsonParseException("Invalid number '" + text.substring(start, pos) + "' at offset " + start);
            }
        }

        Boolean parseBoolean() throws JsonParseException {
            if (text.regionMatches(pos, "true", 0, 4)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (text.regionMatches(pos, "false", 0, 5)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw new JsonParseException("Invalid literal at offset " + pos);
        }

        Object parseNull() throws JsonParseException {
            if (text.regionMatches(pos, "null", 0, 4)) {
                pos += 4;
                return null;
            }
            throw new JsonParseException("Invalid literal at offset " + pos);
        }
    }
}
