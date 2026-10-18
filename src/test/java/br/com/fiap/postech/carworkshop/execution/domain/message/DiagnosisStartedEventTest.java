package br.com.fiap.postech.carworkshop.execution.domain.message;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class DiagnosisStartedEventTest {

    private static final Instant STARTED_AT = Instant.parse("2026-10-16T12:00:00Z");

    private static StartDiagnosisCommand command(UUID messageId) {
        return new StartDiagnosisCommand(messageId, 42L, UUID.randomUUID(), "EVIDENCIA-B3-001", "ABC1D23");
    }

    @Test
    void sameCommandAlwaysProducesTheSameMessageId() {
        StartDiagnosisCommand command = command(UUID.fromString("0b8f1c2e-0000-4000-8000-000000000001"));

        UUID first = DiagnosisStartedEvent.from(command, STARTED_AT).messageId();
        UUID second = DiagnosisStartedEvent.from(command, Instant.parse("2026-10-16T13:30:00Z")).messageId();

        assertEquals(first, second);
    }

    @Test
    void differentCommandsProduceDifferentMessageIds() {
        UUID first = DiagnosisStartedEvent.from(command(UUID.randomUUID()), STARTED_AT).messageId();
        UUID second = DiagnosisStartedEvent.from(command(UUID.randomUUID()), STARTED_AT).messageId();

        assertNotEquals(first, second);
    }

    @Test
    void carriesSagaAndCorrelationAndPointsBackToTheCommand() {
        StartDiagnosisCommand command = command(UUID.randomUUID());

        DiagnosisStartedEvent event = DiagnosisStartedEvent.from(command, STARTED_AT);

        assertEquals(command.workOrderId(), event.workOrderId());
        assertEquals(command.sagaId(), event.sagaId());
        assertEquals(command.correlationId(), event.correlationId());
        assertEquals(command.messageId(), event.causationId());
        assertEquals(STARTED_AT, event.startedAt());
        assertEquals(STARTED_AT, event.occurredAt());
        assertEquals(DiagnosisStartedEvent.CURRENT_VERSION, event.version());
        assertEquals("DiagnosisStarted", event.type());
    }
}
