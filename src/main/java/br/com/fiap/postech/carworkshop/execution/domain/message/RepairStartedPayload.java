package br.com.fiap.postech.carworkshop.execution.domain.message;

import java.time.Instant;

public record RepairStartedPayload(Instant startedAt) implements EventPayload {
}
