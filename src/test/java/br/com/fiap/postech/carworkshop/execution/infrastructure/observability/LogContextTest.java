package br.com.fiap.postech.carworkshop.execution.infrastructure.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LogContextTest {

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    void putsTheCorrelationSagaAndBranchInTheContextAndRemovesThemOnClose() {
        UUID sagaId = UUID.randomUUID();

        try (LogContext ignored = LogContext.of("EVIDENCIA-B6-001", sagaId, "SP-01")) {
            assertEquals("EVIDENCIA-B6-001", MDC.get(LogContext.CORRELATION_ID));
            assertEquals(sagaId.toString(), MDC.get(LogContext.SAGA_ID));
            assertEquals("SP-01", MDC.get(LogContext.BRANCH_ID));
        }

        assertNull(MDC.get(LogContext.CORRELATION_ID));
        assertNull(MDC.get(LogContext.SAGA_ID));
        assertNull(MDC.get(LogContext.BRANCH_ID));
    }

    @Test
    void doesNotPutAbsentValuesInTheContext() {
        try (LogContext ignored = LogContext.of("   ", null, " ")) {
            assertNull(MDC.get(LogContext.CORRELATION_ID));
            assertNull(MDC.get(LogContext.SAGA_ID));
            assertNull(MDC.get(LogContext.BRANCH_ID));
        }
    }
}
