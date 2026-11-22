package io.github.elkboilerplate.logger.internal;

import java.time.Instant;
import java.time.temporal.TemporalAccessor;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

/**
 * Bağımlılıksız, minimal JSON yazıcı. Kütüphanenin harici kütüphane (Jackson vb.) gerektirmemesi
 * için kendi JSON üretimini yapar. Sadece yazma yönü desteklenir.
 */
public final class JsonWriter {

    private static final int MAX_DEPTH = 8;
    private final StringBuilder sb;
    private boolean needComma;

    public JsonWriter(int capacity) {
        this.sb = new StringBuilder(capacity);
    }

    public JsonWriter beginObject() {
        comma();
        sb.append('{');
        needComma = false;
        return this;
    }

    public JsonWriter endObject() {
        sb.append('}');
        needComma = true;
        return this;
    }

    public JsonWriter beginArray() {
        comma();
        sb.append('[');
        needComma = false;
        return this;
    }

    public JsonWriter endArray() {
        sb.append(']');
        needComma = true;
        return this;
    }

    /** Nesne içinde anahtar yazar; ardından bir değer yazılmalıdır. */
    public JsonWriter name(String name) {
        comma();
        string(sb, name);
        sb.append(':');
        needComma = false;
        return this;
    }

    public JsonWriter field(String name, String value) {
        name(name);
        return value(value);
    }

    public JsonWriter field(String name, long value) {
        name(name);
        sb.append(value);
        needComma = true;
        return this;
    }

    public JsonWriter value(String value) {
        comma();
        if (value == null) {
            sb.append("null");
        } else {
            string(sb, value);
        }
        needComma = true;
        return this;
    }

    /** Herhangi bir Java nesnesini JSON'a dönüştürür (Map, Collection, dizi, Number, Boolean, tarih, diğerleri toString). */
    public JsonWriter value(Object value) {
        writeAny(value, 0);
        return this;
    }

    private void writeAny(Object value, int depth) {
        if (value == null) {
            comma();
            sb.append("null");
            needComma = true;
        } else if (value instanceof String || value instanceof Character) {
            value(value.toString());
        } else if (value instanceof Boolean) {
            comma();
            sb.append(value);
            needComma = true;
        } else if (value instanceof Number) {
            comma();
            Number n = (Number) value;
            if ((n instanceof Double && (((Double) n).isNaN() || ((Double) n).isInfinite()))
                    || (n instanceof Float && (((Float) n).isNaN() || ((Float) n).isInfinite()))) {
                string(sb, n.toString());
            } else {
                sb.append(n);
            }
            needComma = true;
        } else if (depth >= MAX_DEPTH) {
            value(safeToString(value));
        } else if (value instanceof Map) {
            beginObject();
            for (Map.Entry<?, ?> e : ((Map<?, ?>) value).entrySet()) {
                name(String.valueOf(e.getKey()));
                writeAny(e.getValue(), depth + 1);
            }
            endObject();
        } else if (value instanceof Collection) {
            beginArray();
            for (Object o : (Collection<?>) value) {
                writeAny(o, depth + 1);
            }
            endArray();
        } else if (value instanceof Object[]) {
            beginArray();
            for (Object o : (Object[]) value) {
                writeAny(o, depth + 1);
            }
            endArray();
        } else if (value.getClass().isArray()) {
            beginArray();
            int len = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < len; i++) {
                writeAny(java.lang.reflect.Array.get(value, i), depth + 1);
            }
            endArray();
        } else if (value instanceof Date) {
            value(((Date) value).toInstant().toString());
        } else if (value instanceof Instant || value instanceof TemporalAccessor) {
            value(value.toString());
        } else {
            value(safeToString(value));
        }
    }

    private static String safeToString(Object o) {
        try {
            return String.valueOf(o);
        } catch (Throwable t) {
            return "[FAILED toString(): " + t.getClass().getName() + "]";
        }
    }

    private void comma() {
        if (needComma) {
            sb.append(',');
        }
    }

    public static void string(StringBuilder out, String s) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                case '\b':
                    out.append("\\b");
                    break;
                case '\f':
                    out.append("\\f");
                    break;
                default:
                    if (c < 0x20 || c == ' ' || c == ' ') {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        out.append('"');
    }

    @Override
    public String toString() {
        return sb.toString();
    }
}
