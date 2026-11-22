package io.github.elkboilerplate.logger.internal;

/** {@link MessageFormatter} sonucu: biçimlenmiş metin + (varsa) argümanlardan çıkarılan exception. */
public final class FormattedMessage {
    private final String message;
    private final Throwable throwable;

    FormattedMessage(String message, Throwable throwable) {
        this.message = message;
        this.throwable = throwable;
    }

    public String getMessage() {
        return message;
    }

    public Throwable getThrowable() {
        return throwable;
    }
}
