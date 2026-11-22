package io.github.elkboilerplate.logger.internal;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * Logstash'e gönderilemeyen olayları yerel dosyaya JSON-lines olarak yazar ({@code elk.fallback.file.*}).
 * Dosya, ELK'ya sonradan elle aktarılabilir: {@code nc <logstash> 5000 < elk-fallback.log}
 * Dosya {@code max.size.mb}'ı aşınca {@code .1} uzantısıyla tek seferlik döndürülür.
 */
public final class FallbackFileSink {

    private final Path path;
    private final long maxBytes;

    public FallbackFileSink(String path, long maxBytes) {
        this.path = Paths.get(path);
        this.maxBytes = maxBytes;
    }

    public synchronized boolean write(List<String> jsonLines) {
        if (jsonLines.isEmpty()) {
            return true;
        }
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (Files.exists(path) && Files.size(path) > maxBytes) {
                Files.move(path, Paths.get(path.toString() + ".1"), StandardCopyOption.REPLACE_EXISTING);
            }
            try (BufferedWriter w = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                for (String line : jsonLines) {
                    w.write(line);
                    w.write('\n');
                }
            }
            return true;
        } catch (IOException e) {
            InternalLog.errorRateLimited("fallback dosyasına yazılamadı (" + path.toAbsolutePath() + "): " + e);
            return false;
        }
    }

    public Path getPath() {
        return path;
    }
}
