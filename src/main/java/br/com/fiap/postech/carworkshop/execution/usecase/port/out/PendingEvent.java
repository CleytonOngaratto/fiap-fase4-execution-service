package br.com.fiap.postech.carworkshop.execution.usecase.port.out;

import java.time.Instant;
import java.util.UUID;

public record PendingEvent(UUID messageId,
                           String type,
                           int version,
                           Instant occurredAt,
                           UUID sagaId,
                           Long workOrderId,
                           String branchId,
                           String correlationId,
                           UUID causationId,
                           String payload) {
}
