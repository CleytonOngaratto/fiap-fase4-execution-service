package br.com.fiap.postech.carworkshop.execution.domain.entity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record ExecutionItem(Long workOrderId,
                            String branchId,
                            UUID sagaId,
                            ExecutionStatus status,
                            String statusReason,
                            Instant enqueuedAt,
                            String vehiclePlate,
                            String complaint,
                            String mechanic,
                            String diagnosisDescription,
                            List<DiagnosedItem> diagnosedItems,
                            List<RepairItem> repairItems,
                            long version,
                            Instant updatedAt) {

    public ExecutionItem {
        diagnosedItems = diagnosedItems == null ? List.of() : List.copyOf(diagnosedItems);
        repairItems = repairItems == null ? List.of() : List.copyOf(repairItems);
    }

    public static ExecutionItem queuedForDiagnosis(Long workOrderId, String branchId, UUID sagaId,
                                                   String vehiclePlate, String complaint, Instant now) {
        return new ExecutionItem(workOrderId, branchId, sagaId, ExecutionStatus.DIAGNOSIS_QUEUED, null, now,
                vehiclePlate, complaint, null, null, List.of(), List.of(), 1L, now);
    }

    public Optional<ExecutionItem> startDiagnosis(String mechanic, Instant now) {
        return status == ExecutionStatus.DIAGNOSIS_QUEUED
                ? Optional.of(moved(ExecutionStatus.DIAGNOSIS_IN_PROGRESS, null, enqueuedAt, mechanic,
                        diagnosisDescription, diagnosedItems, repairItems, now))
                : Optional.empty();
    }

    public Optional<ExecutionItem> completeDiagnosis(String description, List<DiagnosedItem> items, Instant now) {
        return status == ExecutionStatus.DIAGNOSIS_IN_PROGRESS
                ? Optional.of(moved(ExecutionStatus.DIAGNOSIS_DONE, null, enqueuedAt, mechanic, description,
                        items, repairItems, now))
                : Optional.empty();
    }

    public Optional<ExecutionItem> failDiagnosis(String reason, Instant now) {
        return status == ExecutionStatus.DIAGNOSIS_IN_PROGRESS
                ? Optional.of(moved(ExecutionStatus.DIAGNOSIS_FAILED, reason, enqueuedAt, mechanic,
                        diagnosisDescription, diagnosedItems, repairItems, now))
                : Optional.empty();
    }

    // enqueuedAt volta a ser agora: a fila de reparo e outra particao do indice e tem a propria ordem de entrada.
    public Optional<ExecutionItem> queueForRepair(List<RepairItem> items, Instant now) {
        return status == ExecutionStatus.DIAGNOSIS_DONE
                ? Optional.of(moved(ExecutionStatus.REPAIR_QUEUED, null, now, null, diagnosisDescription,
                        diagnosedItems, items, now))
                : Optional.empty();
    }

    public Optional<ExecutionItem> startRepair(String mechanic, Instant now) {
        return status == ExecutionStatus.REPAIR_QUEUED
                ? Optional.of(moved(ExecutionStatus.REPAIR_IN_PROGRESS, null, enqueuedAt, mechanic,
                        diagnosisDescription, diagnosedItems, repairItems, now))
                : Optional.empty();
    }

    public Optional<ExecutionItem> completeRepair(Instant now) {
        return status == ExecutionStatus.REPAIR_IN_PROGRESS
                ? Optional.of(moved(ExecutionStatus.REPAIR_DONE, null, enqueuedAt, mechanic,
                        diagnosisDescription, diagnosedItems, repairItems, now))
                : Optional.empty();
    }

    public Optional<ExecutionItem> failRepair(String reason, Instant now) {
        return status == ExecutionStatus.REPAIR_IN_PROGRESS
                ? Optional.of(moved(ExecutionStatus.REPAIR_FAILED, reason, enqueuedAt, mechanic,
                        diagnosisDescription, diagnosedItems, repairItems, now))
                : Optional.empty();
    }

    public Optional<ExecutionItem> cancel(String reason, Instant now) {
        return status.isCancelable()
                ? Optional.of(moved(ExecutionStatus.CANCELED, reason, enqueuedAt, mechanic, diagnosisDescription,
                        diagnosedItems, repairItems, now))
                : Optional.empty();
    }

    private ExecutionItem moved(ExecutionStatus status, String statusReason, Instant enqueuedAt, String mechanic,
                                String diagnosisDescription, List<DiagnosedItem> diagnosedItems,
                                List<RepairItem> repairItems, Instant now) {
        return new ExecutionItem(workOrderId, branchId, sagaId, status, statusReason, enqueuedAt, vehiclePlate,
                complaint, mechanic, diagnosisDescription, diagnosedItems, repairItems, version + 1, now);
    }
}
