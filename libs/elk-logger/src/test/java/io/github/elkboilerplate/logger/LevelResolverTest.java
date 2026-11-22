package io.github.elkboilerplate.logger;

import io.github.elkboilerplate.logger.internal.LevelResolver;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevelResolverTest {

    @Test
    void longestPrefixWins() {
        Map<String, Level> m = new HashMap<>();
        m.put("com.acme", Level.WARN);
        m.put("com.acme.dao", Level.DEBUG);
        LevelResolver r = new LevelResolver(Level.INFO, m);
        assertEquals(Level.DEBUG, r.resolve("com.acme.dao.UserDao"));
        assertEquals(Level.WARN, r.resolve("com.acme.web.Controller"));
        assertEquals(Level.INFO, r.resolve("com.acmex.Other"));
        assertEquals(Level.INFO, r.resolve("org.other.X"));
        assertEquals(Level.DEBUG, r.resolve("com.acme.dao.UserDao$Inner"));
    }

    @Test
    void levelOrdering() {
        assertTrue(Level.ERROR.isAtLeast(Level.WARN));
        assertFalse(Level.DEBUG.isAtLeast(Level.INFO));
        assertFalse(Level.ERROR.isAtLeast(Level.OFF));
        assertEquals(Level.WARN, Level.parse("warning", null));
        assertEquals(Level.ERROR, Level.parse("FATAL", null));
    }
}
