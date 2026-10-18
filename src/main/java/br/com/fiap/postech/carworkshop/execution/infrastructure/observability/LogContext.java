package br.com.fiap.postech.carworkshop.execution.infrastructure.observability;

import org.slf4j.MDC;

import java.util.UUID;

public final class LogContext implements AutoCloseable {

    public static final String CORRELATION_ID = "correlationId";
    public static final String SAGA_ID = "sagaId";

    private LogContext() {
    }

    public static LogContext of(String correlationId, UUID sagaId) {
        put(CORRELATION_ID, correlationId);
        put(SAGA_ID, sagaId == null ? null : sagaId.toString());
        return new LogContext();
    }

    @Override
    public void close() {
        MDC.remove(CORRELATION_ID);
        MDC.remove(SAGA_ID);
    }

    private static void put(String key, String value) {
        if (value != null && !value.isBlank()) {
            MDC.put(key, value);
        }
    }
}
