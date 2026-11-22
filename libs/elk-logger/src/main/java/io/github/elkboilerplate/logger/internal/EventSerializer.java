package io.github.elkboilerplate.logger.internal;

import io.github.elkboilerplate.logger.config.ElkConfig;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * {@link LogEvent} -> tek satır JSON. ÜRETİLEN ALANLAR SÖZLEŞMEDİR (contract):
 * {@code elk-stack/setup/es/index-template.json} mapping'i ve Logstash pipeline'ı bu alanlara göre yazılmıştır.
 * Alan ekler/değiştirirken {@code docs/ai/EVENT-SCHEMA.md} dosyasını ve index template'i birlikte güncelleyin.
 */
public final class EventSerializer {

    private final ElkConfig config;
    private final String hostname;
    private final long pid;

    public EventSerializer(ElkConfig config) {
        this.config = config;
        this.hostname = config.getHostname() != null ? config.getHostname() : detectHostname();
        this.pid = detectPid();
    }

    public String getHostname() {
        return hostname;
    }

    public String toJson(LogEvent e) {
        JsonWriter w = new JsonWriter(512);
        w.beginObject();
        w.field("@timestamp", DateTimeFormatter.ISO_INSTANT.format(e.timestamp.truncatedTo(ChronoUnit.MILLIS)));
        w.field("project", config.getProjectName());
        w.field("application", config.getApplicationName());
        w.field("environment", config.getEnvironment());
        w.field("hostname", hostname);
        w.field("pid", pid);
        w.field("level", e.level.name());
        w.field("level_value", e.level.value());
        w.field("logger", e.loggerName);
        w.field("thread", e.threadName);
        w.field("message", truncate(e.message, config.getMessageMaxLength()));
        if (e.messageTemplate != null) {
            w.field("message_template", truncate(e.messageTemplate, 1024));
        }
        if (!config.getLabels().isEmpty()) {
            w.name("labels").value(config.getLabels());
        }
        if (e.mdc != null && !e.mdc.isEmpty()) {
            w.name("mdc").value(e.mdc);
        }
        if (e.fields != null && !e.fields.isEmpty()) {
            w.name("fields").value(e.fields);
        }
        if (e.tags != null && !e.tags.isEmpty()) {
            w.name("tags").value(e.tags);
        }
        if (e.throwable != null) {
            Throwable root = e.throwable;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            w.name("exception").beginObject();
            w.field("class", e.throwable.getClass().getName());
            w.field("message", truncate(String.valueOf(e.throwable.getMessage()), config.getMessageMaxLength()));
            if (root != e.throwable) {
                w.field("root_class", root.getClass().getName());
                w.field("root_message", truncate(String.valueOf(root.getMessage()), config.getMessageMaxLength()));
            }
            w.field("stacktrace", truncate(stackTrace(e.throwable), config.getStacktraceMaxLength()));
            w.endObject();
        }
        if (e.caller != null) {
            w.name("caller").beginObject();
            w.field("class", e.caller.getClassName());
            w.field("method", e.caller.getMethodName());
            w.field("file", e.caller.getFileName());
            w.field("line", e.caller.getLineNumber());
            w.endObject();
        }
        w.field("lib_version", Version.VERSION);
        w.endObject();
        return w.toString();
    }

    static String stackTrace(Throwable t) {
        StringWriter sw = new StringWriter(1024);
        try (PrintWriter pw = new PrintWriter(sw)) {
            t.printStackTrace(pw);
        } catch (Throwable ignored) {
            return t.toString();
        }
        return sw.toString();
    }

    static String truncate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "...[truncated " + (s.length() - max) + " chars]";
    }

    private static String detectHostname() {
        Map<String, String> env = System.getenv();
        String h = env.get("HOSTNAME");
        if (h == null || h.isEmpty()) {
            h = env.get("COMPUTERNAME");
        }
        if (h == null || h.isEmpty()) {
            try {
                h = InetAddress.getLocalHost().getHostName();
            } catch (Exception ex) {
                h = "unknown-host";
            }
        }
        return h;
    }

    private static long detectPid() {
        try {
            return ProcessHandle.current().pid();
        } catch (Throwable t) {
            return -1;
        }
    }
}
