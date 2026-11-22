package io.github.elkboilerplate.logger.config;

/** Kuyruk ({@code elk.queue.capacity}) dolduğunda ne yapılacağı ({@code elk.overflow.policy}). */
public enum OverflowPolicy {
    /** Yeni gelen olay atılır (varsayılan). Uygulama asla beklemez. */
    DROP_NEWEST,
    /** Kuyruktaki en eski olay atılır, yenisi eklenir. */
    DROP_OLDEST,
    /** Uygulama thread'i en fazla {@code elk.overflow.block.timeout.ms} kadar bekler, sonra olay atılır. */
    BLOCK
}
