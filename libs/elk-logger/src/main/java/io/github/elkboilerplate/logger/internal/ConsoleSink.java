package io.github.elkboilerplate.logger.internal;

import io.github.elkboilerplate.logger.Level;

import java.io.PrintStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Olayları okunabilir tek satır olarak stdout/stderr'e yazar (geliştirme ve ELK'ya erişilemeyen durumlar için).
 * Biçim: {@code 2026-01-31 10:15:30.123 INFO  [main] com.acme.Foo - mesaj}
 */
public final class ConsoleSink {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private final Level threshold;
    private final boolean stderr;

    public ConsoleSink(Level threshold, boolean stderr) {
        this.threshold = threshold;
        this.stderr = stderr;
    }

    public void write(LogEvent e) {
        if (!e.level.isAtLeast(threshold)) {
            return;
        }
        StringBuilder sb = new StringBuilder(128);
        sb.append(TS.format(e.timestamp)).append(' ');
        String lvl = e.level.name();
        sb.append(lvl);
        for (int i = lvl.length(); i < 5; i++) {
            sb.append(' ');
        }
        sb.append(" [").append(e.threadName).append("] ").append(abbreviate(e.loggerName)).append(" - ").append(e.message);
        if (e.fields != null && !e.fields.isEmpty()) {
            sb.append(' ').append(e.fields);
        }
        if (e.throwable != null) {
            sb.append(System.lineSeparator()).append(EventSerializer.stackTrace(e.throwable).trim());
        }
        PrintStream out = stderr ? System.err : System.out;
        out.println(sb);
    }

    /** {@code com.acme.service.OrderService} -> {@code c.a.s.OrderService} */
    static String abbreviate(String name) {
        if (name == null || name.length() < 40) {
            return name;
        }
        String[] parts = name.split("\\.");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            if (!parts[i].isEmpty()) {
                sb.append(parts[i].charAt(0)).append('.');
            }
        }
        return sb.append(parts[parts.length - 1]).toString();
    }
}
