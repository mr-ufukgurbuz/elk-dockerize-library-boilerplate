package io.github.elkboilerplate.logger.internal;

import io.github.elkboilerplate.logger.Level;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Logger adına göre etkin seviyeyi bulur. En uzun eşleşen önek kazanır:
 * {@code elk.level.com.acme=WARN} + {@code elk.level.com.acme.dao=DEBUG} ise
 * {@code com.acme.dao.UserDao} DEBUG, {@code com.acme.web.X} WARN olur.
 * Önek eşleşmesi paket sınırına göre yapılır ({@code com.acme} öneki {@code com.acmex}'i kapsamaz).
 */
public final class LevelResolver {

    private final Level root;
    private final Map<String, Level> levels;

    public LevelResolver(Level root, Map<String, Level> levels) {
        this.root = root;
        this.levels = new ConcurrentHashMap<>(levels);
    }

    public Level resolve(String loggerName) {
        if (loggerName == null || levels.isEmpty()) {
            return root;
        }
        String name = loggerName;
        while (true) {
            Level l = levels.get(name);
            if (l != null) {
                return l;
            }
            int idx = Math.max(name.lastIndexOf('.'), name.lastIndexOf('$'));
            if (idx <= 0) {
                return root;
            }
            name = name.substring(0, idx);
        }
    }

    /** Çalışma zamanında seviye değiştirir. {@code prefix} null veya boşsa ana (root) seviye değişmez; yeni resolver dönülür. */
    public LevelResolver with(String prefix, Level level) {
        Map<String, Level> copy = new ConcurrentHashMap<>(levels);
        if (prefix == null || prefix.isEmpty()) {
            return new LevelResolver(level, copy);
        }
        if (level == null) {
            copy.remove(prefix);
        } else {
            copy.put(prefix, level);
        }
        return new LevelResolver(root, copy);
    }
}
