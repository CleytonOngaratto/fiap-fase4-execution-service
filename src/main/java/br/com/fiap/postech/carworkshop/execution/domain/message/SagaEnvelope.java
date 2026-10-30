package br.com.fiap.postech.carworkshop.execution.domain.message;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

public record SagaEnvelope(UUID messageId,
                           String type,
                           int version,
                           Instant occurredAt,
                           UUID sagaId,
                           Long workOrderId,
                           String branchId,
                           String correlationId,
                           UUID causationId) {

    public static final int CURRENT_VERSION = 1;

    // messageId derivado do comando, nao aleatorio: a reentrega do mesmo comando produz o mesmo id, e com ele
    // tanto o MessageDeduplicationId do SNS FIFO quanto a inbox do orquestrador descartam a republicacao.
    public SagaEnvelope reply(String replyType, Instant replyOccurredAt) {
        String seed = replyType + ":" + messageId;
        return new SagaEnvelope(UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)), replyType,
                CURRENT_VERSION, replyOccurredAt, sagaId, workOrderId, branchId, correlationId, messageId);
    }

    public static SagaEnvelope event(String type, Long workOrderId, UUID sagaId, String branchId,
                                     String correlationId, Instant occurredAt) {
        return new SagaEnvelope(UUID.randomUUID(), type, CURRENT_VERSION, occurredAt, sagaId, workOrderId,
                branchId, correlationId, null);
    }
}
