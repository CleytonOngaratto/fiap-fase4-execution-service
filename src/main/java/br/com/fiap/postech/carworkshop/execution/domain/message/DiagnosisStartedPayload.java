package br.com.fiap.postech.carworkshop.execution.domain.message;

import java.time.Instant;

public record DiagnosisStartedPayload(String mechanic, Instant startedAt) implements EventPayload {
}
