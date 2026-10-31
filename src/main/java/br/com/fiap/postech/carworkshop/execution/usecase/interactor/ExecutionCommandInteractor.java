package br.com.fiap.postech.carworkshop.execution.usecase.interactor;

import br.com.fiap.postech.carworkshop.execution.domain.entity.ExecutionItem;
import br.com.fiap.postech.carworkshop.execution.domain.entity.ExecutionStatus;
import br.com.fiap.postech.carworkshop.execution.domain.entity.ExecutionStep;
import br.com.fiap.postech.carworkshop.execution.domain.message.CancelDiagnosisCommand;
import br.com.fiap.postech.carworkshop.execution.domain.message.EmptyPayload;
import br.com.fiap.postech.carworkshop.execution.domain.message.ExecutionEvent;
import br.com.fiap.postech.carworkshop.execution.domain.message.SagaEnvelope;
import br.com.fiap.postech.carworkshop.execution.domain.message.SagaMessageType;
import br.com.fiap.postech.carworkshop.execution.domain.message.StartDiagnosisCommand;
import br.com.fiap.postech.carworkshop.execution.domain.message.StartRepairCommand;
import br.com.fiap.postech.carworkshop.execution.usecase.port.in.ExecutionCommandUseCase;
import br.com.fiap.postech.carworkshop.execution.usecase.port.out.ExecutionItemRepositoryPort;
import br.com.fiap.postech.carworkshop.execution.usecase.port.out.ExecutionWrite;
import br.com.fiap.postech.carworkshop.execution.usecase.port.out.ExecutionWriteResult;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public class ExecutionCommandInteractor implements ExecutionCommandUseCase {

    private final ExecutionItemRepositoryPort items;
    private final Clock clock;
    private final Duration inboxRetention;

    public ExecutionCommandInteractor(ExecutionItemRepositoryPort items, Clock clock, Duration inboxRetention) {
        this.items = items;
        this.clock = clock;
        this.inboxRetention = inboxRetention;
    }

    @Override
    public ExecutionWriteResult queueDiagnosis(StartDiagnosisCommand command) {
        SagaEnvelope envelope = command.envelope();
        Instant now = Instant.now(clock);
        Optional<ExecutionItem> existing = items.findByWorkOrderId(envelope.workOrderId());
        if (existing.isPresent()) {
            return items.apply(ExecutionWrite.withoutStateChange(envelope.workOrderId(),
                    step(now, SagaMessageType.START_DIAGNOSIS, existing.get().status(),
                            "the work order was already queued"),
                    null, envelope.messageId(), expiry(now)));
        }
        ExecutionItem queued = ExecutionItem.queuedForDiagnosis(envelope.workOrderId(), envelope.branchId(),
                envelope.sagaId(), command.vehiclePlate(), command.complaint(), now);
        return items.apply(ExecutionWrite.create(queued,
                step(now, SagaMessageType.START_DIAGNOSIS, queued.status(), command.complaint()),
                envelope.messageId(), expiry(now)));
    }

    // Responde DiagnosisCanceled em qualquer caso, inclusive sem item nenhum: o orquestrador espera essa
    // confirmacao para fechar o passo de compensacao, e calar deixaria a saga em COMPENSATING para sempre.
    @Override
    public ExecutionWriteResult cancelDiagnosis(CancelDiagnosisCommand command) {
        SagaEnvelope envelope = command.envelope();
        Instant now = Instant.now(clock);
        ExecutionEvent reply = ExecutionEvent.replyTo(envelope, SagaMessageType.DIAGNOSIS_CANCELED,
                new EmptyPayload(), now);
        Optional<ExecutionItem> existing = items.findByWorkOrderId(envelope.workOrderId());
        if (existing.isEmpty()) {
            return items.apply(ExecutionWrite.withoutStateChange(envelope.workOrderId(),
                    step(now, SagaMessageType.CANCEL_DIAGNOSIS, null, command.reason()), reply,
                    envelope.messageId(), expiry(now)));
        }
        ExecutionItem previous = existing.get();
        Optional<ExecutionItem> canceled = previous.cancel(command.reason(), now);
        if (canceled.isEmpty()) {
            return items.apply(ExecutionWrite.withoutStateChange(envelope.workOrderId(),
                    step(now, SagaMessageType.CANCEL_DIAGNOSIS, previous.status(), command.reason()), reply,
                    envelope.messageId(), expiry(now)));
        }
        return items.apply(ExecutionWrite.transitionFromCommand(previous, canceled.get(),
                step(now, SagaMessageType.CANCEL_DIAGNOSIS, canceled.get().status(), command.reason()), reply,
                envelope.messageId(), expiry(now)));
    }

    @Override
    public ExecutionWriteResult queueRepair(StartRepairCommand command) {
        SagaEnvelope envelope = command.envelope();
        Instant now = Instant.now(clock);
        Optional<ExecutionItem> existing = items.findByWorkOrderId(envelope.workOrderId());
        if (existing.isEmpty()) {
            return items.apply(ExecutionWrite.withoutStateChange(envelope.workOrderId(),
                    step(now, SagaMessageType.START_REPAIR, null, "there is no execution item to repair"),
                    null, envelope.messageId(), expiry(now)));
        }
        ExecutionItem previous = existing.get();
        Optional<ExecutionItem> queued = previous.queueForRepair(command.items(), now);
        if (queued.isEmpty()) {
            return items.apply(ExecutionWrite.withoutStateChange(envelope.workOrderId(),
                    step(now, SagaMessageType.START_REPAIR, previous.status(),
                            "the diagnosis is not complete"),
                    null, envelope.messageId(), expiry(now)));
        }
        return items.apply(ExecutionWrite.transitionFromCommand(previous, queued.get(),
                step(now, SagaMessageType.START_REPAIR, queued.get().status(), null), null,
                envelope.messageId(), expiry(now)));
    }

    private Instant expiry(Instant now) {
        return now.plus(inboxRetention);
    }

    private static ExecutionStep step(Instant now, String action, ExecutionStatus status, String detail) {
        return new ExecutionStep(now, action, status, detail, ExecutionStep.SAGA);
    }
}
