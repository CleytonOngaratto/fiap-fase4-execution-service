package br.com.fiap.postech.carworkshop.execution.usecase.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ExecutionOutboxPort {

    List<PendingEvent> claimPending(int limit);

    boolean markSent(Long workOrderId, UUID messageId, Instant sentAt);
}
