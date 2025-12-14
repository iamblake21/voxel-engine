package engine.utils;

import java.util.*;

/**
 * A minimal JSON parser to avoid external dependencies.
 * Supports standard JSON syntax.
 */
public class SimpleJson {

    public static Object parse(String json) {
        return new Parser(json).parse();
    }

    private static class Parser {
        private final String json;
        private int pos = 0;
        private final int length;

        public Parser(String json) {
            this.json = json;
            this.length = json.length();
        }

        public Object parse() {
            skipWhitespace();
            if (pos >= length)
                return null;

            char c = json.charAt(pos);
            if (c == '{')
                return parseObject();
            if (c == '[')
                return parseArray();
            if (c == '"')
                return parseString();
            if (c == 't' || c == 'f')
                return parseBoolean();
            if (c == 'n')
                return parseNull();
            if (Character.isDigit(c) || c == '-')
                return parseNumber();

            throw new RuntimeException("Unexpected character at " + pos + ": " + c);
        }

        private Map<String, Object> parseObject() {
            consume('{');
            Map<String, Object> map = new HashMap<>();

            skipWhitespace();
            if (peek() == '}') {
                consume('}');
                return map;
            }

            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                consume(':');
                Object value = parse();
                map.put(key, value);

                skipWhitespace();
                if (peek() == '}') {
                    break;
                }
                consume(',');
            }
            consume('}');
            return map;
        }

        private List<Object> parseArray() {
            consume('[');
            List<Object> list = new ArrayList<>();

            skipWhitespace();
            if (peek() == ']') {
                consume(']');
                return list;
            }

            while (true) {
                list.add(parse());
                skipWhitespace();
                if (peek() == ']') {
                    break;
                }
                consume(',');
            }
            consume(']');
            return list;
        }

        private String parseString() {
            consume('"');
            StringBuilder sb = new StringBuilder();
            while (pos < length) {
                char c = json.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (pos >= length)
                        throw new RuntimeException("Unterminated escape");
                    char esc = json.charAt(pos++);
                    if (esc == 'n')
                        sb.append('\n');
                    else if (esc == 't')
                        sb.append('\t');
                    else if (esc == 'r')
                        sb.append('\r');
                    else if (esc == '"')
                        sb.append('"');
                    else if (esc == '\\')
                        sb.append('\\');
                    else
                        sb.append(esc);
                } else {
                    sb.append(c);
                }
            }
            throw new RuntimeException("Unterminated string");
        }

        private Object parseNumber() {
            int start = pos;
            if (peek() == '-')
                pos++;
            while (pos < length && Character.isDigit(json.charAt(pos)))
                pos++;
            if (pos < length && json.charAt(pos) == '.') {
                pos++;
                while (pos < length && Character.isDigit(json.charAt(pos)))
                    pos++;
                return Double.parseDouble(json.substring(start, pos));
            }
            return Integer.parseInt(json.substring(start, pos));
        }

        private boolean parseBoolean() {
            if (json.startsWith("true", pos)) {
                pos += 4;
                return true;
            }
            if (json.startsWith("false", pos)) {
                pos += 5;
                return false;
            }
            throw new RuntimeException("Invalid boolean");
        }

        private Object parseNull() {
            if (json.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw new RuntimeException("Invalid null");
        }

        private void skipWhitespace() {
            while (pos < length && Character.isWhitespace(json.charAt(pos))) {
                pos++;
            }
        }

        private void consume(char c) {
            skipWhitespace();
            if (pos >= length || json.charAt(pos) != c) {
                throw new RuntimeException("Expected " + c + " at " + pos);
            }
            pos++;
        }

        private char peek() {
            if (pos >= length)
                return 0;
            return json.charAt(pos);
        }
    }
}
