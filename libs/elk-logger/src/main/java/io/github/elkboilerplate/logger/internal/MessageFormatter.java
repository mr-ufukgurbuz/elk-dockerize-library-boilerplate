package io.github.elkboilerplate.logger.internal;

import java.util.Arrays;

/**
 * SLF4J uyumlu {@code {}} yer tutucu biçimlendirici.
 * <ul>
 *   <li>{@code {}} sırayla argümanlarla değiştirilir.</li>
 *   <li>{@code \{}} kaçış dizisi: literal {@code {}} yazar.</li>
 *   <li>Son argüman {@link Throwable} ise ve bir yer tutucu tarafından tüketilmediyse exception kabul edilir.</li>
 *   <li>Diziler {@code Arrays.deepToString} ile yazılır; toString() hata verirse mesaj bozulmaz.</li>
 * </ul>
 */
public final class MessageFormatter {

    private MessageFormatter() {
    }

    public static FormattedMessage format(String pattern, Object[] args) {
        if (args == null || args.length == 0) {
            return new FormattedMessage(pattern == null ? "null" : pattern, null);
        }
        Throwable throwable = null;
        int usable = args.length;
        Object last = args[args.length - 1];
        if (last instanceof Throwable) {
            throwable = (Throwable) last;
            usable = args.length - 1;
        }
        if (pattern == null) {
            return new FormattedMessage("null", throwable);
        }

        StringBuilder sb = new StringBuilder(pattern.length() + 32);
        int argIndex = 0;
        int i = 0;
        int len = pattern.length();
        while (i < len) {
            char c = pattern.charAt(i);
            if (c == '{' && i + 1 < len && pattern.charAt(i + 1) == '}') {
                boolean escaped = i > 0 && pattern.charAt(i - 1) == '\\';
                boolean doubleEscaped = escaped && i > 1 && pattern.charAt(i - 2) == '\\';
                if (escaped && !doubleEscaped) {
                    // "\{}" -> "{}" : sondaki ters bölüyü sil
                    sb.setLength(sb.length() - 1);
                    sb.append("{}");
                } else {
                    if (doubleEscaped) {
                        // "\\{}" -> "\" + arg
                        sb.setLength(sb.length() - 1);
                    }
                    if (argIndex < usable) {
                        appendArg(sb, args[argIndex++]);
                    } else if (argIndex == usable && throwable != null && usable < args.length) {
                        // yer tutucu sayısı, sondaki throwable'ı da kapsıyor -> mesaja yaz, exception kabul etme
                        appendArg(sb, args[argIndex++]);
                        throwable = null;
                    } else {
                        sb.append("{}");
                    }
                }
                i += 2;
            } else {
                sb.append(c);
                i++;
            }
        }
        return new FormattedMessage(sb.toString(), throwable);
    }

    private static void appendArg(StringBuilder sb, Object arg) {
        if (arg == null) {
            sb.append("null");
            return;
        }
        try {
            if (arg.getClass().isArray()) {
                if (arg instanceof Object[]) {
                    sb.append(Arrays.deepToString((Object[]) arg));
                } else if (arg instanceof int[]) {
                    sb.append(Arrays.toString((int[]) arg));
                } else if (arg instanceof long[]) {
                    sb.append(Arrays.toString((long[]) arg));
                } else if (arg instanceof byte[]) {
                    sb.append(Arrays.toString((byte[]) arg));
                } else if (arg instanceof short[]) {
                    sb.append(Arrays.toString((short[]) arg));
                } else if (arg instanceof char[]) {
                    sb.append(Arrays.toString((char[]) arg));
                } else if (arg instanceof boolean[]) {
                    sb.append(Arrays.toString((boolean[]) arg));
                } else if (arg instanceof float[]) {
                    sb.append(Arrays.toString((float[]) arg));
                } else if (arg instanceof double[]) {
                    sb.append(Arrays.toString((double[]) arg));
                }
            } else {
                sb.append(arg);
            }
        } catch (Throwable t) {
            sb.append("[FAILED toString(): ").append(t.getClass().getName()).append(']');
        }
    }
}
