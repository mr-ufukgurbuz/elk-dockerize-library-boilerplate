package io.github.elkboilerplate.logger.slf4j;

import io.github.elkboilerplate.logger.ElkMDC;
import org.slf4j.spi.MDCAdapter;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/** {@code org.slf4j.MDC} çağrılarını {@link ElkMDC}'ye yönlendirir; böylece iki API aynı MDC'yi paylaşır. */
final class ElkMDCAdapter implements MDCAdapter {

    private final ThreadLocal<Map<String, Deque<String>>> deques = ThreadLocal.withInitial(HashMap::new);

    @Override public void put(String key, String val) { ElkMDC.put(key, val); }
    @Override public String get(String key) { return ElkMDC.get(key); }
    @Override public void remove(String key) { ElkMDC.remove(key); }
    @Override public void clear() { ElkMDC.clear(); deques.remove(); }
    @Override public Map<String, String> getCopyOfContextMap() { return ElkMDC.getCopyOfContextMap(); }

    @Override
    @SuppressWarnings("unchecked")
    public void setContextMap(Map<String, String> contextMap) {
        ElkMDC.setContextMap(contextMap);
    }

    @Override
    public void pushByKey(String key, String value) {
        deques.get().computeIfAbsent(key, k -> new ArrayDeque<>()).push(value);
    }

    @Override
    public String popByKey(String key) {
        Deque<String> d = deques.get().get(key);
        return d == null ? null : d.poll();
    }

    @Override
    public Deque<String> getCopyOfDequeByKey(String key) {
        Deque<String> d = deques.get().get(key);
        return d == null ? null : new ArrayDeque<>(d);
    }

    @Override
    public void clearDequeByKey(String key) {
        Deque<String> d = deques.get().get(key);
        if (d != null) {
            d.clear();
        }
    }
}
