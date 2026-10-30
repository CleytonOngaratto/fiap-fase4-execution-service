package br.com.fiap.postech.carworkshop.execution.infrastructure.messaging;

import br.com.fiap.postech.carworkshop.execution.adapter.gateway.SagaEnvelopeCodec;
import br.com.fiap.postech.carworkshop.execution.domain.message.SagaMessageType;
import br.com.fiap.postech.carworkshop.execution.usecase.port.in.ExecutionCommandUseCase;
import br.com.fiap.postech.carworkshop.execution.usecase.port.out.ExecutionWriteResult;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ExecutionCommandHandler {

    @Inject
    SagaEnvelopeCodec codec;

    @Inject
    ExecutionCommandUseCase commands;

    public ExecutionWriteResult apply(JsonNode node, String type) {
        return switch (type) {
            case SagaMessageType.START_DIAGNOSIS -> commands.queueDiagnosis(codec.toStartDiagnosis(node));
            case SagaMessageType.CANCEL_DIAGNOSIS -> commands.cancelDiagnosis(codec.toCancelDiagnosis(node));
            case SagaMessageType.START_REPAIR -> commands.queueRepair(codec.toStartRepair(node));
            default -> throw new IllegalStateException("Command type " + type + " has no handler.");
        };
    }
}
