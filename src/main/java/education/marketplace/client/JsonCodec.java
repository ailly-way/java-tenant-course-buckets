package education.marketplace.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JsonCodec {
    private JsonCodec() {}

    public static Object parse(String json) {
        Parser parser = new Parser(json);
        Object value = parser.value();
        parser.whitespace();
        if (!parser.finished()) throw new IllegalArgumentException("Unexpected JSON suffix");
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(String json) {
        Object value = parse(json);
        if (!(value instanceof Map<?, ?>)) throw new IllegalArgumentException("Expected a JSON object");
        return (Map<String, Object>) value;
    }

    public static String stringify(Object value) {
        if (value == null) return "null";
        if (value instanceof String text) return '"' + escape(text) + '"';
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) {
            StringBuilder out = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) out.append(',');
                first = false;
                out.append(stringify(entry.getKey().toString())).append(':').append(stringify(entry.getValue()));
            }
            return out.append('}').toString();
        }
        if (value instanceof Iterable<?> values) {
            StringBuilder out = new StringBuilder("[");
            boolean first = true;
            for (Object item : values) {
                if (!first) out.append(',');
                first = false;
                out.append(stringify(item));
            }
            return out.append(']').toString();
        }
        throw new IllegalArgumentException("Cannot encode " + value.getClass().getName());
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static final class Parser {
        private final String source;
        private int at;

        private Parser(String source) { this.source = source; }
        private boolean finished() { return at == source.length(); }
        private void whitespace() { while (!finished() && Character.isWhitespace(source.charAt(at))) at++; }

        private Object value() {
            whitespace();
            if (finished()) throw new IllegalArgumentException("Expected JSON value");
            char c = source.charAt(at);
            if (c == '{') return objectValue();
            if (c == '[') return arrayValue();
            if (c == '"') return stringValue();
            if (source.startsWith("true", at)) { at += 4; return true; }
            if (source.startsWith("false", at)) { at += 5; return false; }
            if (source.startsWith("null", at)) { at += 4; return null; }
            return numberValue();
        }

        private Map<String, Object> objectValue() {
            at++;
            Map<String, Object> result = new LinkedHashMap<>();
            whitespace();
            if (take('}')) return result;
            do {
                whitespace();
                String key = stringValue();
                whitespace();
                require(':');
                result.put(key, value());
                whitespace();
            } while (take(','));
            require('}');
            return result;
        }

        private List<Object> arrayValue() {
            at++;
            List<Object> result = new ArrayList<>();
            whitespace();
            if (take(']')) return result;
            do { result.add(value()); whitespace(); } while (take(','));
            require(']');
            return result;
        }

        private String stringValue() {
            require('"');
            StringBuilder result = new StringBuilder();
            while (!finished()) {
                char c = source.charAt(at++);
                if (c == '"') return result.toString();
                if (c != '\\') { result.append(c); continue; }
                if (finished()) throw new IllegalArgumentException("Incomplete JSON escape");
                char escaped = source.charAt(at++);
                switch (escaped) {
                    case '"', '\\', '/' -> result.append(escaped);
                    case 'b' -> result.append('\b');
                    case 'f' -> result.append('\f');
                    case 'n' -> result.append('\n');
                    case 'r' -> result.append('\r');
                    case 't' -> result.append('\t');
                    case 'u' -> {
                        if (at + 4 > source.length()) throw new IllegalArgumentException("Incomplete unicode escape");
                        result.append((char) Integer.parseInt(source.substring(at, at + 4), 16));
                        at += 4;
                    }
                    default -> throw new IllegalArgumentException("Unknown JSON escape");
                }
            }
            throw new IllegalArgumentException("Unclosed JSON string");
        }

        private Number numberValue() {
            int start = at;
            while (!finished() && "-+0123456789.eE".indexOf(source.charAt(at)) >= 0) at++;
            String raw = source.substring(start, at);
            try { return raw.contains(".") || raw.contains("e") || raw.contains("E")
                    ? Double.parseDouble(raw) : Long.parseLong(raw); }
            catch (NumberFormatException error) { throw new IllegalArgumentException("Invalid JSON number", error); }
        }

        private boolean take(char expected) {
            if (!finished() && source.charAt(at) == expected) { at++; return true; }
            return false;
        }

        private void require(char expected) {
            if (!take(expected)) throw new IllegalArgumentException("Expected '" + expected + "'");
        }
    }
}
