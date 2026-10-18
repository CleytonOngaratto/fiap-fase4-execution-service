package br.com.fiap.postech.carworkshop.execution.domain.message;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

public record DiagnosisStartedEvent(UUID messageId,
                                    int version,
                                    Instant occurredAt,
                                    Long workOrderId,
                                    UUID sagaId,
                                    String correlationId,
                                    UUID causationId,
                                    Instant startedAt) {

    public static final int CURRENT_VERSION = 1;

    // messageId derivado do comando, nao aleatorio: reentrega do mesmo comando produz o mesmo id, que serve de
    // MessageDeduplicationId e faz o SNS FIFO descartar a republicacao dentro da janela de 5 min.
    public static DiagnosisStartedEvent from(StartDiagnosisCommand command, Instant startedAt) {
        String seed = SagaMessageType.DIAGNOSIS_STARTED + ":" + command.messageId();
        return new DiagnosisStartedEvent(
                UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)),
                CURRENT_VERSION,
                startedAt,
                command.workOrderId(),
                command.sagaId(),
                command.correlationId(),
                command.messageId(),
                startedAt);
    }

    public String type() {
        return SagaMessageType.DIAGNOSIS_STARTED;
    }
}
