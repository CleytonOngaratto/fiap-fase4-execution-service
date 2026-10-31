package br.com.fiap.postech.carworkshop.execution.usecase.interactor;

import br.com.fiap.postech.carworkshop.execution.domain.entity.DiagnosedItem;
import br.com.fiap.postech.carworkshop.execution.domain.entity.ExecutionItem;
import br.com.fiap.postech.carworkshop.execution.domain.entity.ExecutionStatus;
import br.com.fiap.postech.carworkshop.execution.domain.entity.ExecutionStep;
import br.com.fiap.postech.carworkshop.execution.domain.exception.ExecutionConflictException;
import br.com.fiap.postech.carworkshop.execution.domain.exception.ExecutionItemNotFoundException;
import br.com.fiap.postech.carworkshop.execution.domain.exception.InvalidDiagnosisException;
import br.com.fiap.postech.carworkshop.execution.domain.message.DiagnosisCompletedPayload;
import br.com.fiap.postech.carworkshop.execution.domain.message.DiagnosisStartedPayload;
import br.com.fiap.postech.carworkshop.execution.domain.message.EventPayload;
import br.com.fiap.postech.carworkshop.execution.domain.message.ExecutionEvent;
import br.com.fiap.postech.carworkshop.execution.domain.message.FailurePayload;
import br.com.fiap.postech.carworkshop.execution.domain.message.RepairCompletedPayload;
import br.com.fiap.postech.carworkshop.execution.domain.message.RepairStartedPayload;
import br.com.fiap.postech.carworkshop.execution.domain.message.SagaEnvelope;
import br.com.fiap.postech.carworkshop.execution.domain.message.SagaMessageType;
import br.com.fiap.postech.carworkshop.execution.usecase.port.in.MechanicUseCase;
import br.com.fiap.postech.carworkshop.execution.usecase.port.out.CatalogValidationPort;
import br.com.fiap.postech.carworkshop.execution.usecase.port.out.ExecutionItemRepositoryPort;
import br.com.fiap.postech.carworkshop.execution.usecase.port.out.ExecutionWrite;
import br.com.fiap.postech.carworkshop.execution.usecase.port.out.ExecutionWriteResult;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class MechanicInteractor implements MechanicUseCase {

    private final ExecutionItemRepositoryPort items;
    private final CatalogValidationPort catalog;
    private final Clock clock;

    public MechanicInteractor(ExecutionItemRepositoryPort items, CatalogValidationPort catalog, Clock clock) {
        this.items = items;
        this.catalog = catalog;
        this.clock = clock;
    }

    @Override
    public List<ExecutionItem> queue(String branchId, ExecutionStatus status, int limit) {
        return items.queueOf(branchId, status, limit);
    }

    @Override
    public void startDiagnosis(Long workOrderId, String mechanic, String correlationId) {
        ExecutionItem previous = require(workOrderId);
        Instant now = Instant.now(clock);
        apply(previous, previous.startDiagnosis(mechanic, now), SagaMessageType.DIAGNOSIS_STARTED,
                new DiagnosisStartedPayload(mechanic, now), null, mechanic, correlationId, now);
    }

    @Override
    public void completeDiagnosis(Long workOrderId, String description, List<DiagnosedItem> items,
                                  String mechanic, String correlationId) {
        requireWellFormed(description, items);
        catalog.assertKnown(items);
        ExecutionItem previous = require(workOrderId);
        Instant now = Instant.now(clock);
        apply(previous, previous.completeDiagnosis(description, items, now),
                SagaMessageType.DIAGNOSIS_COMPLETED, new DiagnosisCompletedPayload(description, items),
                description, mechanic, correlationId, now);
    }

    @Override
    public void failDiagnosis(Long workOrderId, String reason, String mechanic, String correlationId) {
        ExecutionItem previous = require(workOrderId);
        Instant now = Instant.now(clock);
        apply(previous, previous.failDiagnosis(reason, now), SagaMessageType.DIAGNOSIS_FAILED,
                new FailurePayload(reason), reason, mechanic, correlationId, now);
    }

    @Override
    public void startRepair(Long workOrderId, String mechanic, String correlationId) {
        ExecutionItem previous = require(workOrderId);
        Instant now = Instant.now(clock);
        apply(previous, previous.startRepair(mechanic, now), SagaMessageType.REPAIR_STARTED,
                new RepairStartedPayload(now), null, mechanic, correlationId, now);
    }

    @Override
    public void completeRepair(Long workOrderId, String mechanic, String correlationId) {
        ExecutionItem previous = require(workOrderId);
        Instant now = Instant.now(clock);
        apply(previous, previous.completeRepair(now), SagaMessageType.REPAIR_COMPLETED,
                new RepairCompletedPayload(now), null, mechanic, correlationId, now);
    }

    @Override
    public void failRepair(Long workOrderId, String reason, String mechanic, String correlationId) {
        ExecutionItem previous = require(workOrderId);
        Instant now = Instant.now(clock);
        apply(previous, previous.failRepair(reason, now), SagaMessageType.REPAIR_FAILED,
                new FailurePayload(reason), reason, mechanic, correlationId, now);
    }

    private ExecutionItem require(Long workOrderId) {
        return items.findByWorkOrderId(workOrderId)
                .orElseThrow(() -> new ExecutionItemNotFoundException(workOrderId));
    }

    private static void requireWellFormed(String description, List<DiagnosedItem> diagnosed) {
        if (description == null || description.isBlank()) {
            throw new InvalidDiagnosisException("The diagnosis needs a description.");
        }
        if (diagnosed == null || diagnosed.isEmpty()) {
            throw new InvalidDiagnosisException("The diagnosis needs at least one item.");
        }
        for (DiagnosedItem item : diagnosed) {
            if (!item.isWellFormed()) {
                throw new InvalidDiagnosisException("The item " + item.type() + "/" + item.catalogId()
                        + " is not a valid catalog entry.");
            }
        }
    }

    // Transicao pedida por pessoa recusa com conflito, ao contrario da dirigida pela saga, que devolve vazio e
    // segue: quem chamou precisa saber que nao aconteceu.
    private void apply(ExecutionItem previous, Optional<ExecutionItem> moved, String eventType,
                       EventPayload payload, String detail, String mechanic, String correlationId,
                       Instant now) {
        ExecutionItem next = moved.orElseThrow(() -> new ExecutionConflictException(previous.status()));
        ExecutionEvent event = new ExecutionEvent(SagaEnvelope.event(eventType, next.workOrderId(),
                next.sagaId(), next.branchId(), correlationId, now), payload);
        ExecutionStep step = new ExecutionStep(now, eventType, next.status(), detail, mechanic);
        if (items.apply(ExecutionWrite.transition(previous, next, step, event))
                == ExecutionWriteResult.CONFLICT) {
            throw new ExecutionConflictException(previous.status());
        }
    }
}
