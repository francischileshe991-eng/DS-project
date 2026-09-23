package util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Json {
    private Json() {}

    public static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String stringify(Object o) {
        if (o == null) return "null";
        if (o instanceof String) return "\"" + escape((String) o) + "\"";
        if (o instanceof Boolean || o instanceof Number) return String.valueOf(o);
        if (o instanceof Map<?, ?>) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) {
                if (!first) sb.append(",");
                first = false;
                sb.append(stringify(String.valueOf(e.getKey()))).append(":").append(stringify(e.getValue()));
            }
            return sb.append("}").toString();
        }
        if (o instanceof List<?>) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object e : (List<?>) o) {
                if (!first) sb.append(",");
                first = false;
                sb.append(stringify(e));
            }
            return sb.append("]").toString();
        }
        if (o instanceof int[]) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (int e : (int[]) o) {
                if (!first) sb.append(",");
                first = false;
                sb.append(e);
            }
            return sb.append("]").toString();
        }
        return "\"" + escape(String.valueOf(o)) + "\"";
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parse(String json) {
        Parser p = new Parser(json);
        Object v = p.parseValue();
        if (v instanceof Map) return (Map<String, Object>) v;
        throw new IllegalArgumentException("Expected JSON object at top level: " + json);
    }

    public static String asString(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? null : String.valueOf(v);
    }

    public static int asInt(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String) return Integer.parseInt((String) v);
        throw new IllegalArgumentException("Missing or non-numeric int field: " + key);
    }

    public static int[] asIntArray(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (!(v instanceof List)) throw new IllegalArgumentException("Missing array field: " + key);
        List<?> l = (List<?>) v;
        int[] out = new int[l.size()];
        for (int i = 0; i < l.size(); i++) out[i] = ((Number) l.get(i)).intValue();
        return out;
    }

    private static final class Parser {
        private final String s;
        private int i = 0;

        Parser(String s) {
            this.s = s;
        }

        Object parseValue() {
            skipWs();
            if (i >= s.length()) throw new IllegalArgumentException("Unexpected end of JSON");
            char c = s.charAt(i);
            if (c == '{') return parseObject();
            if (c == '[') return parseArray();
            if (c == '"') return parseString();
            if (c == 't' || c == 'f') return parseBool();
            if (c == 'n') return parseNull();
            if (c == '-' || (c >= '0' && c <= '9')) return parseNumber();
            throw new IllegalArgumentException("Unexpected char '" + c + "' at index " + i);
        }

        Map<String, Object> parseObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            expect('{');
            skipWs();
            if (peek() == '}') { i++; return map; }
            while (true) {
                skipWs();
                String key = parseString();
                skipWs();
                expect(':');
                map.put(key, parseValue());
                skipWs();
                char c = next();
                if (c == ',') continue;
                if (c == '}') return map;
                throw new IllegalArgumentException("Expected ',' or '}' at index " + (i - 1));
            }
        }

        List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            expect('[');
            skipWs();
            if (peek() == ']') { i++; return list; }
            while (true) {
                list.add(parseValue());
                skipWs();
                char c = next();
                if (c == ',') continue;
                if (c == ']') return list;
                throw new IllegalArgumentException("Expected ',' or ']' at index " + (i - 1));
            }
        }

        String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (i >= s.length()) break;
                    char e = s.charAt(i++);
                    switch (e) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'u':
                            if (i + 4 <= s.length()) {
                                sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                                i += 4;
                            }
                            break;
                        default: sb.append(e);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new IllegalArgumentException("Unterminated string in JSON");
        }

        Object parseNumber() {
            int start = i;
            while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.' ||
                    s.charAt(i) == 'e' || s.charAt(i) == 'E' || s.charAt(i) == '+' || s.charAt(i) == '-')) {
                i++;
            }
            String num = s.substring(start, i);
            if (num.contains(".") || num.contains("e") || num.contains("E")) return Double.parseDouble(num);
            return Long.parseLong(num);
        }

        Object parseBool() {
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            throw new IllegalArgumentException("Bad boolean literal at index " + i);
        }

        Object parseNull() {
            if (s.startsWith("null", i)) { i += 4; return null; }
            throw new IllegalArgumentException("Bad null literal at index " + i);
        }

        void skipWs() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        char peek() {
            return i < s.length() ? s.charAt(i) : '\0';
        }

        char next() {
            if (i >= s.length()) throw new IllegalArgumentException("Unexpected end of JSON");
            return s.charAt(i++);
        }

        void expect(char c) {
            skipWs();
            char got = next();
            if (got != c) throw new IllegalArgumentException("Expected '" + c + "' but got '" + got + "' at index " + (i - 1));
        }
    }
}