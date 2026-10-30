package br.com.fiap.postech.carworkshop.execution.domain.message;

import java.time.Instant;

public record ExecutionEvent(SagaEnvelope envelope, EventPayload payload) {

    public static ExecutionEvent replyTo(SagaEnvelope cause, String type, EventPayload payload,
                                         Instant occurredAt) {
        return new ExecutionEvent(cause.reply(type, occurredAt), payload);
    }
}
