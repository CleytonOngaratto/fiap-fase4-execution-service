package br.com.fiap.postech.carworkshop.execution.usecase.interactor;

import br.com.fiap.postech.carworkshop.execution.domain.message.DiagnosisStartedEvent;
import br.com.fiap.postech.carworkshop.execution.domain.message.StartDiagnosisCommand;
import br.com.fiap.postech.carworkshop.execution.usecase.port.out.DiagnosisEventPublisherPort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiagnosisInteractorTest {

    private static final Instant NOW = Instant.parse("2026-10-16T12:00:00Z");

    private final List<DiagnosisStartedEvent> published = new ArrayList<>();
    private final DiagnosisEventPublisherPort publisher = published::add;
    private final DiagnosisInteractor interactor =
            new DiagnosisInteractor(publisher, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void start_publishesDiagnosisStartedForTheCommand() {
        UUID commandId = UUID.randomUUID();
        UUID sagaId = UUID.randomUUID();

        interactor.start(new StartDiagnosisCommand(commandId, 7L, sagaId, "EVIDENCIA-B3-001", "ABC1D23"));

        assertEquals(1, published.size());
        DiagnosisStartedEvent event = published.get(0);
        assertEquals(7L, event.workOrderId());
        assertEquals(sagaId, event.sagaId());
        assertEquals(commandId, event.causationId());
        assertEquals(NOW, event.startedAt());
    }
}
