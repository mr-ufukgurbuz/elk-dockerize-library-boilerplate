package io.github.elkboilerplate.logger;

import io.github.elkboilerplate.logger.internal.FormattedMessage;
import io.github.elkboilerplate.logger.internal.MessageFormatter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class MessageFormatterTest {

    @Test
    void replacesPlaceholdersInOrder() {
        assertEquals("a=1 b=2", MessageFormatter.format("a={} b={}", new Object[]{1, 2}).getMessage());
    }

    @Test
    void missingArgsKeepPlaceholder() {
        assertEquals("a=1 b={}", MessageFormatter.format("a={} b={}", new Object[]{1}).getMessage());
    }

    @Test
    void escapedPlaceholderIsLiteral() {
        assertEquals("set {} = 5", MessageFormatter.format("set \\{} = {}", new Object[]{5}).getMessage());
    }

    @Test
    void trailingThrowableIsExtracted() {
        RuntimeException ex = new RuntimeException("x");
        FormattedMessage fm = MessageFormatter.format("failed id={}", new Object[]{7, ex});
        assertEquals("failed id=7", fm.getMessage());
        assertSame(ex, fm.getThrowable());
    }

    @Test
    void throwableConsumedByPlaceholderIsNotException() {
        RuntimeException ex = new RuntimeException("x");
        FormattedMessage fm = MessageFormatter.format("err={}", new Object[]{ex});
        assertEquals("err=java.lang.RuntimeException: x", fm.getMessage());
        assertNull(fm.getThrowable());
    }

    @Test
    void arraysAndNulls() {
        assertEquals("[1, 2] null [a, [b]]",
                MessageFormatter.format("{} {} {}", new Object[]{new int[]{1, 2}, null, new Object[]{"a", new String[]{"b"}}}).getMessage());
    }

    @Test
    void brokenToStringDoesNotThrow() {
        Object bad = new Object() {
            @Override
            public String toString() {
                throw new IllegalStateException("boom");
            }
        };
        assertEquals("v=[FAILED toString(): java.lang.IllegalStateException]",
                MessageFormatter.format("v={}", new Object[]{bad}).getMessage());
    }
}
