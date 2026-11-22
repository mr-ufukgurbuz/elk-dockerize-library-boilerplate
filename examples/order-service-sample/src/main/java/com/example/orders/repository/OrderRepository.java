package com.example.orders.repository;

import io.github.elkboilerplate.logger.ElkLogger;
import io.github.elkboilerplate.logger.ElkLoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Sahte veri erişim katmanı. elk-logger.properties içinde
 * {@code elk.level.com.example.orders.repository=DEBUG} olduğu için bu paketin DEBUG logları da gönderilir,
 * diğer paketlerde ise sadece INFO ve üstü gider.
 */
public final class OrderRepository {

    private static final ElkLogger log = ElkLoggerFactory.getLogger(OrderRepository.class);
    private final AtomicLong sequence = new AtomicLong(100_000);

    public long save(String customerId, String product, int quantity) {
        long id = sequence.incrementAndGet();
        if (log.isDebugEnabled()) {
            log.debug("INSERT INTO orders(id, customer, product, qty) VALUES ({}, {}, {}, {})", id, customerId, product, quantity);
        }
        return id;
    }
}
