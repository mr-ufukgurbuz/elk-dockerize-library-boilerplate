package io.github.elkboilerplate.logger.internal;

import io.github.elkboilerplate.logger.config.ElkConfig;

/** {@code elk.transport} değerine göre {@link Transport} üretir. */
public final class Transports {

    private Transports() {
    }

    /** @return transport; {@code NONE} için null */
    public static Transport create(ElkConfig config) {
        switch (config.getTransport()) {
            case TCP:
                return new TcpTransport(config);
            case HTTP:
                return new HttpTransport(config);
            case NONE:
            default:
                return null;
        }
    }
}
